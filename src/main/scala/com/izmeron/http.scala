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

import akka.actor.{ ActorLogging, Props, ActorSystem }
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpEntity.ChunkStreamPart
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import akka.stream.actor.ActorSubscriberMessage.{ OnError, OnComplete, OnNext }
import akka.stream.actor._
import akka.stream.io.Framing
import akka.stream.scaladsl._
import akka.util.ByteString
import com.izmeron.out.{ JsonOutputModule, OutputWriter }

import scala.annotation.tailrec
import scala.collection.mutable
import scala.concurrent.Future

object http {
  object Server {
    import scalaz.std.AllInstances._
    val S = implicitly[scalaz.Semigroup[String]]
    val M = implicitly[scalaz.Monoid[Map[String, List[Result]]]]

    val sep = ByteString("\n")

    def parseOrder(bs: ByteString): Order = {
      val items = bs.utf8String.split(';')
      Order(items(0), items(13).toInt)
    }

    def innerSource(order: Order, index: mutable.Map[String, RawResult],
                    lenghtThreshold: Int, minLenght: Int,
                    log: akka.event.LoggingAdapter)(implicit ctx: scala.concurrent.ExecutionContext) = Source {
      Future {
        index.get(order.kd).fold(List.empty[Result]) { raw ⇒
          distributeWithinGroup(lenghtThreshold, minLenght, log)(groupByOptimalNumber(order, lenghtThreshold, minLenght, log)(raw))
        }
      }(ctx)
    }

    def cuttingFlow(lenghtThreshold: Int, minLenght: Int, log: akka.event.LoggingAdapter) =
      Flow[List[Result]].map { list ⇒ cuttingStockProblem(list, lenghtThreshold, minLenght, log) }

    //http POST http://127.0.0.1:8001/orders < ./csv/metal2pipes2.csv Accept:text/plain
    def apply(port: Int, lenghtThreshold: Int, minLenght: Int, index: mutable.Map[String, RawResult],
              ctx: scala.concurrent.ExecutionContext)(implicit writer: OutputWriter[JsonOutputModule],
                                                      sys: ActorSystem, mat: ActorMaterializer): Future[akka.http.scaladsl.Http.ServerBinding] = {
      val route =
        path("version") {
          get {
            complete {
              "Here's some data... or would be if we had data."
            }
          }
        } ~ path("orders") {
          post {
            extractRequest { req ⇒
              complete {

                val streamer = sys.actorOf(Props(classOf[Streamer], lenghtThreshold, s"./out/plan_${System.currentTimeMillis()}.json", "json", writer)
                  .withDispatcher("akka.planner"))

                val sub = ActorSubscriber[List[Combination]](streamer)
                val pub = ActorPublisher[ChunkStreamPart](streamer)

                val mapSource = (req.entity.dataBytes.via(Framing.delimiter(sep, Int.MaxValue, true).map(parseOrder))
                  .grouped(parallelism).map { ords ⇒
                    Source() { implicit b ⇒
                      import FlowGraph.Implicits._
                      val groupSource = ords.map { order ⇒
                        innerSource(order, index, lenghtThreshold, minLenght, sys.log)(ctx)
                          .map(list ⇒ list.headOption.fold(Map[String, List[Result]]())(head ⇒ Map(head.groupKey -> list)))
                      }
                      val merge = b.add(Merge[Map[String, List[Result]]](ords.size))
                      groupSource.foreach(_ ~> merge)
                      merge.out
                    }
                  }.flatten(akka.stream.scaladsl.FlattenStrategy.concat[Map[String, List[Result]]])
                  .fold(M.zero)((acc, c) ⇒ M.append(acc, c))
                  .mapConcat(_.values.toList))

                val flow = FlowGraph.closed() { implicit b ⇒
                  import FlowGraph.Implicits._
                  val balancer = b.add(Balance[List[Result]](parallelism))
                  val merge = b.add(Merge[List[Combination]](parallelism))
                  mapSource ~> balancer
                  for (i ← 0 until parallelism) {
                    balancer ~> cuttingFlow(lenghtThreshold, minLenght, sys.log) ~> merge
                  }
                  merge ~> Sink(sub)
                }

                flow.run()
                HttpResponse(entity = HttpEntity.Chunked(ContentTypes.`text/plain(UTF-8)`, Source(pub)))
              }
            }
          }
        }

      Http()(sys).bindAndHandle(route, "127.0.0.1", port)(mat)
    }
  }

  class Streamer(lenghtThreshold: Int, outFile: String, outFormat: String, writer: OutputWriter[JsonOutputModule])
      extends ActorSubscriber with ActorPublisher[ChunkStreamPart] with ActorLogging {
    private val bufferSize = 100
    private val queue = mutable.Queue[ChunkStreamPart]()

    override protected val requestStrategy = new MaxInFlightRequestStrategy(bufferSize) {
      override val inFlightInternally = queue.size
    }

    override def receive: Receive = {
      case OnNext(cmbs: List[Combination]) ⇒
        log.info("push element")
        val line = writer.convert(writer.monoidMapper(lenghtThreshold, cmbs))
        queue += ChunkStreamPart(line)

      case OnComplete ⇒
        log.debug("client has been exhausted")
        flush

        /*val result = writer convert acc
        log.debug(s"Output: $outFile ${Ansi.green(result.toString)}")
        writer.write(result, outFile).unsafePerformIO()
        */
        log.debug(s"OnComplete ${queue.size}")
        context.system.stop(self)

      case OnError(ex) ⇒
        log.debug(s"OnError:" + ex.getMessage)
        onError(ex)

      case ActorPublisherMessage.Request(n) ⇒
        log.debug(s"request $n")
        loop(n)

      case ActorPublisherMessage.SubscriptionTimeoutExceeded ⇒
        onComplete()
        context.stop(self)

      case ActorPublisherMessage.Cancel ⇒
        log.debug("client has been canceled")
        cancel()
        context.stop(self)
    }

    def flush = {
      while ((isActive && totalDemand > 0) && !queue.isEmpty) {
        onNext(queue.dequeue())
      }
    }

    def loop(n: Long) = {
      @tailrec def go(n: Long): Unit = {
        if ((isActive && totalDemand > 0) && !queue.isEmpty && n > 0) {
          log.info(s"pull element ${queue.size}")
          onNext(queue.dequeue())
          go(n - 1)
        } else ()
      }
      go(n)
    }
  }
}

/*
val requestHandler: HttpRequest ⇒ HttpResponse = {
    case HttpRequest(GET, Uri.Path("/"), _, _, _) ⇒
      val dir = "/test-data/"
      println(s"\r\n handling request")
      val s = Source(files.listFiles().filter(_.getName().endsWith(".txt")).toIterator) map { file =>
        println(s"\r\n reading file ${file.getName}")
        java.nio.file.Files.readAllBytes(Paths.get(file.getAbsolutePath))
      } map { byteArray =>
        println(s"\r\n getting bytes ${byteArray.length}")
        new GzipCompressor().compress(ByteString(byteArray))
      }
      HttpResponse(entity = HttpEntity.Chunked.fromData(MediaTypes.`application/x-gzip`, s))
    case _: HttpRequest ⇒ HttpResponse(404, entity = "Unknown resource!")
  }
  streamingServer foreach { case Http.ServerBinding(localAddress, connectionStream) =>
    Source(connectionStream) foreach { case Http.IncomingConnection(remoteAddress, requestProducer, responseConsumer) =>
      println(s"\r\n Accepted new connection from $remoteAddress")
      Source(requestProducer).map(requestHandler).to(Sink(responseConsumer)).run()
    }
  }
 */ 