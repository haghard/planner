/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.izmeron

import akka.actor.{ActorRef, Props}
import akka.stream.actor.ActorSubscriberMessage.{OnComplete, OnNext}
import akka.stream.actor.{OneByOneRequestStrategy, ActorSubscriber}
import akka.stream.scaladsl._
import com.izmeron.out.{OutputWriter, OutputModule}

import scala.collection.mutable
import scala.concurrent.{Await, Future}

package object commands {

  sealed trait CliCommand {
    def start(): Unit
  }

  object Exit extends CliCommand {
    override def start() = System.exit(0)
  }

  object Empty extends CliCommand {
    override def start() = ()
  }

  object StaticCheck {
    val ok = "Ok"
    val separator = ";"
    val rule0 = "0. Max lenght rule violation"
    val rule1 = "1. Multiplicity violation"

    def apply(path: String, minLenght: Int, lenghtThreshold: Int): StaticCheck =
      new StaticCheck(path, minLenght, lenghtThreshold)
  }

  final class StaticCheck(override val indexPath: String, override val minLenght: Int,
                          override val lenghtThreshold: Int) extends CliCommand with Indexing  {
    override val log = system.log
    import StaticCheck._
    override def start() = {
      (maxLengthCheck zip multiplicity).map { case (max, error) =>
        if (lenghtThreshold < max) println(s"${Ansi.blueMessage(rule0)}: ${Ansi.errorMessage(s"Config value $lenghtThreshold but $max has been found")}")
        else println(s"${Ansi.blueMessage(rule0)}: ${Ansi.blueMessage(ok)}")

        if (error.isEmpty) println(s"${Ansi.blueMessage(rule1)}: ${Ansi.blueMessage(ok)}")
        else println(s"${Ansi.blueMessage(rule1)}: ${Ansi.green(error.size.toString)}: ${Ansi.errorMessage(error.mkString(separator))}")
      }.onFailure {
        case e: Throwable =>
          log.error(s"Check rules error ${Ansi.errorMessage(e.getMessage())}")
          println(s"${Ansi.blueMessage("Check rules error")}: ${Ansi.errorMessage(e.getMessage())}")
      }
    }
  }

  object Plan {
    import scalaz.std.AllInstances._
    val M = implicitly[scalaz.Monoid[Map[String, List[Result]]]]

    def innerSource(order: Order, index: mutable.Map[String, RawResult],
                    lenghtThreshold: Int, minLenght: Int, log: akka.event.LoggingAdapter)
                   (implicit dispatcher: scala.concurrent.ExecutionContext) = Source {
      Future {
        val raw = index(order.kd)
        distributeWithinGroup(lenghtThreshold, minLenght, log)(groupByOptimalNumber(order, lenghtThreshold, minLenght, log)(raw))
      }(dispatcher)
    }

    private def cutting(lenghtThreshold: Int, minLenght: Int, log: akka.event.LoggingAdapter) =
      Flow[List[Result]].buffer(1, akka.stream.OverflowStrategy.backpressure)
      .map { list => cuttingStockProblem(list, lenghtThreshold, minLenght, log) }

    private def orderSource(orders: List[Order], index: mutable.Map[String, RawResult],
                          lenghtThreshold: Int, minLenght: Int, log: akka.event.LoggingAdapter)
                          (implicit dispatcher: scala.concurrent.ExecutionContext) =
      Source(orders).grouped(parallelism).map { ords =>
        Source() { implicit b =>
          import FlowGraph.Implicits._
          val groupSource = ords.map { order =>
            innerSource(order, index, lenghtThreshold, minLenght, log)
              .map(list => list.headOption.fold(Map[String, List[Result]]())(head ⇒ Map(head.groupKey -> list)))
          }
          val merge = b.add(Merge[Map[String, List[Result]]](ords.size))
          groupSource.foreach(_ ~> merge)
          merge.out
        }
      }.flatten(akka.stream.scaladsl.FlattenStrategy.concat[Map[String, List[Result]]])
        .fold(M.zero)((acc,c) => M.append(acc,c))
        .mapConcat(_.values.toList)

    private def cuttingFlow(lenghtThreshold: Int, minLenght: Int, log: akka.event.LoggingAdapter) = Flow() { implicit b =>
      import FlowGraph.Implicits._
      val balancer = b.add(Balance[List[Result]](parallelism))
      val merge = b.add(Merge[List[Combination]](parallelism))
      (0 until parallelism).foreach { _ => balancer ~> cutting(lenghtThreshold, minLenght, log) ~> merge }
      (balancer.in, merge.out)
    }

    def apply[T <: OutputModule](path: String, outputDir: String, outFormat: String, minLenght: Int, lenghtThreshold: Int)
                                (implicit writer: OutputWriter[T]) =
      new Plan[T](path, outputDir, outFormat, minLenght, lenghtThreshold, writer)
  }

  final class Plan[T <: OutputModule](override val indexPath: String, outputDir: String, outFormat: String, override val minLenght: Int,
                                      override val lenghtThreshold: Int, writer: OutputWriter[T]) extends CliCommand with Indexing {
    import Plan._
    import akka.pattern.ask
    import scala.concurrent.duration._
    implicit val timeout = akka.util.Timeout(360 seconds)

    override val log = system.log
    /**
     * Computation graph
     *
     * File Source    Parallel Source                                              Parallel Flows
     * +----------+   +-----------+                                                +------------+
     * |csv_line0 |---|distribute |--+                                          +- |cuttingStock|----+
     * +----------+   +-----------+  |  Fan-in stage                            |  +------------+    |
     * +----------+   +-----------+  | +------+  +-----------------+  +-------+ |  +------------+    |   +-----+   +----------+
     * |csv_line1 |---|distribute |----|Merge |--|flatten/mapConcat|--|Balance|----|cuttingStock |-------|Merge|---|Sink actor|
     * +----------+   +-----------+  | +------+  +-----------------+  +-------+ |  +------------+    |   +-----+   +----------+
     *                               |                                          |  +------------+    |
     * +----------+   +-----------+  |                                          +--|cuttingStock|----+
     * |csv_line2 |---|distribute |--+                                             +------------+
     * +----------+   +-----------+
     */
    override def start() =
      Await.ready(
        (indexedOrders.flatMap { case (orders, index) =>
        (orderSource(orders, index, lenghtThreshold, minLenght, system.log) via cuttingFlow(lenghtThreshold, minLenght, system.log))
          .runWith(Sink.actorSubscriber(Props(classOf[ResultAggregator[T]], lenghtThreshold, outputDir, outFormat, writer))) ? 'GetResult
        }).recoverWith {
          case e: Exception =>
            println(Ansi.red(e.getMessage))
            log.error(e.getMessage)
            Future.failed(e)
        }, 365 seconds)
  }

  class ResultAggregator[T <: OutputModule](lenghtThreshold: Int, outDir: String, outFormat:String, writer: OutputWriter[T]) extends ActorSubscriber {
    var acc = writer.Zero
    var requestor: Option[ActorRef] = None
    val message = "Result has been written in file"
    val exp = s"$message(.+)"
    val start = System.currentTimeMillis
    override protected val requestStrategy = OneByOneRequestStrategy

    override def receive: Receive = {
      case OnNext(cmbs: List[Combination]) =>
        acc = writer.monoid.append(acc, writer.monoidMapper(lenghtThreshold, cmbs))

      case OnComplete =>
        val path = s"plan_${System.currentTimeMillis()}.${outFormat}"
        val file = (message + path).replaceAll(exp, s"${Console.RED}$$1${Console.RESET}")
        println(s"$message: $file")
        val result = writer convert acc
        writer.write(result, s"$outDir/$path").unsafePerformIO()
        println(s"Latency: ${Ansi.red((System.currentTimeMillis - start).toString)} mills")
        requestor.foreach(_ ! 'Ok)
        context.system.stop(self)
      case 'GetResult =>
        requestor = Option(sender())
    }
  }
}