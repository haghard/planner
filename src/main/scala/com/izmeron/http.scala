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

import akka.event.LoggingAdapter
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.stream.{ FlowShape, SourceShape, OverflowStrategy, ActorMaterializer }
import akka.stream.actor._
import akka.stream.io.Framing
import akka.stream.scaladsl._
import akka.util.ByteString
import akka.actor.{ ActorLogging, Props, ActorSystem }
import com.izmeron.out.{ JsonOutputModule, OutputWriter }
import akka.stream.actor.ActorSubscriberMessage.{ OnError, OnComplete, OnNext }

import scala.annotation.tailrec
import scala.collection.mutable
import scala.concurrent.Future

object http {
  object Server {
    import scalaz.std.AllInstances._
    val M = implicitly[scalaz.Monoid[Map[String, List[Result]]]]

    val sep = ByteString("\n")

    private def parseOrder(bs: ByteString): Order = {
      val items = bs.utf8String.split(';')
      Order(items(0), items(13).toInt)
    }

    private def innerSource(order: Order, index: mutable.Map[String, RawResult],
                            lenghtThreshold: Int, minLenght: Int,
                            log: akka.event.LoggingAdapter)(implicit ctx: scala.concurrent.ExecutionContext) = Source.fromFuture {
      Future {
        index.get(order.kd).fold(List.empty[Result]) { raw ⇒
          distributeWithinGroup(lenghtThreshold, minLenght, log)(groupByOptimalNumber(order, lenghtThreshold, minLenght, log)(raw))
        }
      }(ctx)
    }

    private def cutting(lenghtThreshold: Int, minLenght: Int, log: akka.event.LoggingAdapter) =
      Flow[List[Result]].buffer(1, OverflowStrategy.backpressure).map { list ⇒ cuttingStockProblem(list, lenghtThreshold, minLenght, log) }

    private def plannerSource(req: HttpRequest, index: mutable.Map[String, RawResult],
                              lenghtThreshold: Int, minLenght: Int, log: LoggingAdapter)(implicit ctx: scala.concurrent.ExecutionContext) =
      req.entity.dataBytes.via(Framing.delimiter(sep, Int.MaxValue, true).map(parseOrder))
        .grouped(parallelism).map { ords ⇒
          Source.fromGraph(GraphDSL.create() { implicit b ⇒
            import GraphDSL.Implicits._
            val groupSource = ords.map { order ⇒
              innerSource(order, index, lenghtThreshold, minLenght, log)(ctx)
                .map(list ⇒ list.headOption.fold(Map[String, List[Result]]())(head ⇒ Map(head.groupKey -> list)))
            }
            val merge = b.add(Merge[Map[String, List[Result]]](ords.size))
            groupSource.foreach(_ ~> merge)
            SourceShape(merge.out)
          })
        }.flatMapConcat(identity)
        .fold(M.zero)((acc, c) ⇒ M.append(acc, c))
        .mapConcat(_.values.toList)

    private def cuttingGraph(lenghtThreshold: Int, minLenght: Int, log: LoggingAdapter) =
      GraphDSL.create() { implicit b ⇒
        import GraphDSL.Implicits._
        val balancer = b.add(Balance[List[Result]](parallelism))
        val merge = b.add(Merge[List[Combination]](parallelism))
        for (i ← 0 until parallelism) {
          balancer ~> cutting(lenghtThreshold, minLenght, log) ~> merge
        }
        FlowShape(balancer.in, merge.out)
      }

    //http POST http://127.0.0.1:8001/orders < ./csv/metal2pipes2.csv Accept:application/json --stream
    //http POST http://127.0.0.1:8001/orders < ./csv/metal2pipes3.csv Accept:application/json --stream
    def apply(port: Int, lenghtThreshold: Int, minLenght: Int, index: mutable.Map[String, RawResult])(implicit writer: OutputWriter[JsonOutputModule],
                                                                                                      ctx: scala.concurrent.ExecutionContext, sys: ActorSystem, mat: ActorMaterializer): Future[ServerBinding] = {
      val route =
        path("version") {
          post {
            complete {
              "Here's some data... or would be if we had data."
            }
          }
        } ~ path("orders") {
          post {
            extractRequest { req ⇒
              complete {
                val MaxBufferSize = mat.settings.maxInputBufferSize
                val streamer = sys.actorOf(Props(classOf[ResponseStreamer], lenghtThreshold, MaxBufferSize, writer).withDispatcher("akka.planner"))
                val sub = ActorSubscriber[List[Combination]](streamer)
                val sink = Sink.fromSubscriber[List[Combination]](sub)
                val pub = ActorPublisher[ByteString](streamer)
                val source = Source.fromPublisher[ByteString](pub)
                ((plannerSource(req, index, lenghtThreshold, minLenght, sys.log) via cuttingGraph(lenghtThreshold, minLenght, sys.log))
                  runWith sink)
                HttpResponse(entity = HttpEntity.Chunked.fromData(ContentTypes.`application/json`, source))
              }
            }
          }
        }

      Http()(sys).bindAndHandle(route, "127.0.0.1", port)(mat)
    }
  }

  class ResponseStreamer(lenghtThreshold: Int, MaxBufferSize: Int, writer: OutputWriter[JsonOutputModule])
      extends ActorSubscriber with ActorPublisher[ByteString] with ActorLogging {
    private val queue = mutable.Queue[ByteString]()
    private var read = false
    val start = System.currentTimeMillis()
    override protected val requestStrategy = new MaxInFlightRequestStrategy(MaxBufferSize) {
      override def inFlightInternally = queue.size
    }

    private val waitLastChunk: Receive = {
      case ActorPublisherMessage.Request(n) ⇒
        loop(n)
        (context stop self)

      case ActorPublisherMessage.SubscriptionTimeoutExceeded ⇒
        onComplete()
        (context stop self)

      case ActorPublisherMessage.Cancel ⇒
        log.debug("client has closed the connection")
        cancel()
        (context stop self)
    }

    override def receive: Receive = {
      case OnNext(cmbs: List[Combination]) ⇒
        //log.info(s"Queue:${buffer.size} Demand:$totalDemand")
        val line = writer.convert(writer monoidMapper (lenghtThreshold, cmbs))
        queue += ByteString(line)
        if (totalDemand > 0)
          loop(totalDemand)

      case OnComplete ⇒
        flush
        onNext(ByteString(s"""latency: ${System.currentTimeMillis - start}"""))
        if (read) (context stop self)
        //log.debug(s"completed with buffer:${buffer.size}")
        else (context become waitLastChunk)

      case OnError(ex) ⇒
        log.debug(s"onError: ${ex.getMessage}")
        onError(ex)

      case ActorPublisherMessage.Request(n) ⇒
      //we just relay on totalDemand

      case ActorPublisherMessage.SubscriptionTimeoutExceeded ⇒
        onComplete()
        (context stop self)

      case ActorPublisherMessage.Cancel ⇒
        log.debug("client has closed the connection")
        cancel()
        (context stop self)
    }

    def flush = {
      if ((isActive && totalDemand > 0) && !queue.isEmpty) {
        onNext(queue.dequeue)
      }
    }

    def loop(n: Long) = {
      @tailrec def go(n: Long): Long = {
        if ((isActive && totalDemand > 0) && !queue.isEmpty) {
          onNext(queue.dequeue())
          go(n - 1)
        } else n
      }
      read = true
      go(n)
    }
  }
}