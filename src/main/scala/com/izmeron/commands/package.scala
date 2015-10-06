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

import akka.actor.Props
import akka.stream.actor.ActorSubscriberMessage.{OnComplete, OnNext}
import akka.stream.actor.{OneByOneRequestStrategy, ActorSubscriber}
import akka.stream.scaladsl._
import com.izmeron.out.{OutputWriter, OutputModule}

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
    override val log = org.apache.log4j.Logger.getLogger("static-check")
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
    def apply[T <: OutputModule](path: String, outputDir: String, outFormat: String, minLenght: Int, lenghtThreshold: Int)
                                (implicit writer: OutputWriter[T]) =
      new Plan[T](path, outputDir, outFormat, minLenght, lenghtThreshold, writer)
  }

  final class Plan[T <: OutputModule](override val indexPath: String, outputDir: String, outFormat: String, override val minLenght: Int,
                      override val lenghtThreshold: Int, writer: OutputWriter[T]) extends CliCommand with Indexing {
    import scalaz.std.AllInstances._
    val M = implicitly[scalaz.Monoid[Map[String, List[Result]]]]
    override val log = org.apache.log4j.Logger.getLogger("planner")
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
    override def start(): Unit = {
      indexedOrders.map { case (orders, index) =>
        val mapSource = Source(orders).grouped(parallelism).map { ords =>
          Source() { implicit b =>
            import FlowGraph.Implicits._
            val groupSource = ords.map { order =>
              innerSource(order, index).map(list => list.headOption.fold(Map[String, List[Result]]())(head ⇒ Map(head.groupKey -> list)))
            }
            val merge = b.add(Merge[Map[String, List[Result]]](ords.size))
            groupSource.foreach(_ ~> merge)
            merge.out
          }
        }.flatten(akka.stream.scaladsl.FlattenStrategy.concat[Map[String, List[Result]]])
          .fold(M.zero)((acc,c) => M.append(acc,c))
          .mapConcat(_.values.toList)

        def cuttingFlow() = Flow[List[Result]].map { list => cuttingStockProblem(list, lenghtThreshold, minLenght, log) }

        val flow = FlowGraph.closed() { implicit b =>
          import FlowGraph.Implicits._
          val balancer = b.add(Balance[List[Result]](parallelism))
          val merge = b.add(Merge[List[Combination]](parallelism))
          mapSource ~> balancer
          for (i <- 0 until parallelism) {
            balancer ~> cuttingFlow() ~> merge
          }
          merge ~> Sink.actorSubscriber(Props(classOf[ResultAggregator[T]], lenghtThreshold, outputDir, outFormat, writer))
        }

        flow.run()
      }.onFailure {
        case e: Throwable =>
          println(Ansi.red(e.getMessage))
          log.error(e.getMessage)
      }
    }
  }

  class ResultAggregator[T <: OutputModule](lenghtThreshold: Int, outDir: String, outFormat:String, writer: OutputWriter[T]) extends ActorSubscriber {
    override protected val requestStrategy = OneByOneRequestStrategy
    var acc = writer.Zero
    val message = "Result has been written in file"
    val exp = s"$message(.+)"
    override def receive: Receive = {
      case OnNext(cmbs: List[Combination]) =>
        acc = writer.monoid.append(acc, writer.monoidMapper(lenghtThreshold, cmbs))

      case OnComplete =>
        val path = s"plan_${System.currentTimeMillis()}.${outFormat}"
        val file = (message + path).replaceAll(exp, s"${Console.RED}$$1${Console.RESET}")
        println(s"$message: $file")
        val result = writer convert acc
        println(Ansi.green(result.toString))
        writer.write(result, s"$outDir/$path").unsafePerformIO()
        context.system.stop(self)
    }
  }
}