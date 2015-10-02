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

import org.http4s.{ TransferCoding, Response }
import scalaz.concurrent.Task
import scalaz.{ -\/, \/- }
import org.apache.log4j.Logger
import org.http4s.server.blaze.BlazeBuilder
import org.http4s.dsl._
import org.http4s.headers.{ `Transfer-Encoding` }

object http {

  implicit class ChunkedResponse(response: Task[Response]) {
    def chunked: Task[Response] = {
      response.putHeaders(`Transfer-Encoding`(TransferCoding.chunked))
    }
  }

  class PlannerServer(val path0: String, httpPort0: Int,
                      val log0: org.apache.log4j.Logger,
                      val minLenght0: Int,
                      val lenghtThreshold0: Int,
                      val v: Version) {
    private val aggregator = new OrigamiAggregator {
      override def path = path0
      override def minLenght = minLenght0
      override def log: Logger = log0
      override def lenghtThreshold = lenghtThreshold0
      override def start(): Unit = ???
    }

    def start() = {
      log0.debug("★ ★ ★ ★ ★ ★  Index creation has been started  ★ ★ ★ ★ ★ ★")

      aggregator.createIndex.runAsync {
        case \/-((\/-(index), None)) ⇒
          log0.info("★ ★ ★  Index has been created  ★ ★ ★ ★ ★ ★")
          BlazeBuilder.bindHttp(httpPort0, "127.0.0.1")
            .mountService(OrderService(aggregator), "/")
            .run
            .awaitShutdown()
          log0.debug(s"★ ★ ★ ★ ★ ★  Http server started on 127.0.0.1:$httpPort0 ★ ★ ★ ★ ★ ★")
        case -\/(ex) ⇒
          log0.error(s"Error while building index: ${ex.getMessage}")
          System.exit(-1)
        case \/-((-\/(ex), None)) ⇒
          log0.error(s"Error while building index: ${ex.getMessage}")
          System.exit(-1)
        case \/-((_, Some(ex))) ⇒
          log0.error(s"Finalizer error while building index: ${ex.getMessage}")
          System.exit(-1)
      }
    }

    def shutdown(): Unit = ???
  }

  /*
    with ScalazFlowSupport {
    import scalaz.stream.async
    import scalaz.stream.csv._
    import scalaz.stream.merge
    import OutputWriter._
    val output = OutputWriter[JsonOutputModule]

    override def intent: Intent = {
      case req ⇒
        implicit val ex = PlannerEx
        implicit val CpuIntensive = scalaz.concurrent.Strategy.Executor(PlannerEx)
        implicit val Codec: scala.io.Codec = scala.io.Codec.UTF8
        implicit val responder: unfiltered.Async.Responder[Any] = req
        req match {
          case GET(Path("/info")) ⇒
            req.respond(textResponse { server.log.debug("GET /info"); server.v.toString })

          //echo '94100.00.00.072;5' | curl -d @- http://127.0.0.1:9001/orders
          //http POST http://127.0.0.1:9001/orders < ./cvs/order.csv
          case POST(Path("/orders")) ⇒
            forkTask {
              val queue = async.boundedQueue[List[Result]](parallelism * parallelism)
              (inputReader(rowsR[Order](req.inputStream, ';').map(server.lookupFromIndex(_, index)), queue).drain merge merge.mergeN(parallelism)(cuttingWorkers(queue)))
                .foldMap(output.monoidMapper(lenghtThreshold, _))(output.monoid)
                .runLast
                .map { _.fold(errorResponse(output.empty))(json ⇒ jsonResponse(output.convert(json))) }
            }

          case invalid ⇒
            responder.respond(methodNotAllowed(s"Method:${invalid.method} Uri:${invalid.uri}"))
        }
    }
  }*/
}