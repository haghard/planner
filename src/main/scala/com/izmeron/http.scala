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

import org.http4s.dsl._
import scalaz.{ -\/, \/- }
import scalaz.concurrent.Task
import java.util.concurrent.Executors
import org.http4s.server.blaze.BlazeBuilder
import org.http4s.{ TransferCoding, Response }
import org.http4s.headers.`Transfer-Encoding`

object http {

  implicit class ChunkedResponse(response: Task[Response]) {
    def chunked: Task[Response] = {
      response.putHeaders(`Transfer-Encoding`(TransferCoding.chunked))
    }
  }

  val Buffer = 1024 * 1000 * 50
  val httpExec = Executors.newFixedThreadPool(Runtime.getRuntime.availableProcessors(),
    new NamedThreadFactory("http-worker"))

  class Server(val indexPath: String, httpPort0: Int, val log0: org.apache.log4j.Logger,
               val minLenght0: Int, val lenghtThreshold0: Int, val v: Version) {
    private val aggregator = new OrigamiAggregator with ScalazFlowSupport {
      override val path = indexPath
      override val minLenght = minLenght0
      override val log = log0
      override val lenghtThreshold = lenghtThreshold0
    }

    def start() = {
      log0.debug("★ ★ ★ ★ ★ ★  Index creation has been started  ★ ★ ★ ★ ★ ★")
      aggregator.createIndex.runAsync {
        case \/-((\/-(index), None)) ⇒
          log0.info(s"Server has been started with IndexPath:$indexPath  MinLenght: $minLenght0 LenghtThreshold:$lenghtThreshold0 version: $v")
          BlazeBuilder.bindHttp(httpPort0, "127.0.0.1")
            .mountService(OrderService(aggregator, index, lenghtThreshold0, log0), "/")
            .withServiceExecutor(httpExec)
            .withNio2(true)
            .withBufferSize(Buffer)
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
  }
}