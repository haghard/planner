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

import java.util.concurrent.ExecutorService

import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.group.DefaultChannelGroup
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.util.concurrent.GlobalEventExecutor
import unfiltered.Async
import unfiltered.netty.async.Plan.Intent
import unfiltered.netty.{ SocketBinding, ServerErrorResponse, async }
import unfiltered.request._
import unfiltered.response._

import scala.collection.mutable
import scala.concurrent.{ ExecutionContext, Future }
import scala.io.Codec
import scala.util.control.NonFatal
import scala.util.{ Failure, Success }
import scalaz.{ -\/, \/- }
import scalaz.concurrent.Task

object http {

  trait AsyncContext {
    val END = "\r\n"

    def forkFuture[A](body: ⇒ Future[ResponseFunction[A]])(implicit responder: Async.Responder[A], executionContext: ExecutionContext): Unit = {
      try {
        body.onComplete {
          case Success(result) ⇒ responder.respond(result)
          case Failure(error)  ⇒ responder.respond(errorResponse(error))
        }
      } catch {
        case NonFatal(e) ⇒ responder.respond(errorResponse(e))
      }
    }

    def forkTask(task: Task[ResponseFunction[Any]])(implicit responder: Async.Responder[Any], ctx: ExecutorService): Unit = {
      try {
        Task.fork(task)(ctx).runAsync {
          case \/-(result) ⇒ responder.respond(result)
          case -\/(error)  ⇒ responder.respond(errorResponse(error))
        }
      } catch {
        case NonFatal(e) ⇒ responder.respond(errorResponse(e))
      }
    }

    def textResponse[A](content: String): ResponseFunction[A] = Ok ~> PlainTextContent ~> ResponseString(content + END)

    def jsonResponse[A](json: String): ResponseFunction[A] = Ok ~> JsonContent ~> ResponseString(json + END)

    def errorResponse[A](error: String): ResponseFunction[A] = BadRequest ~> PlainTextContent ~> ResponseString(error + END)

    def errorResponse[A](error: Throwable): ResponseFunction[A] = errorResponse(error.toString)
  }

  class RestServer(override val path: String, httpPort: Int, override val log: org.apache.log4j.Logger,
                   override val minLenght: Int, override val lenghtThreshold: Int, val v: Version) {
    mixin: Planner ⇒

    @volatile var http: Option[unfiltered.netty.Server] = None
    private val host = unfiltered.netty.Server.allInterfacesHost

    val engine = new unfiltered.netty.Engine {
      override val acceptor = new NioEventLoopGroup(1, new NamedThreadFactory("boss"))
      override val workers = new NioEventLoopGroup(Runtime.getRuntime.availableProcessors() * 2, new NamedThreadFactory("worker"))
      override val channels = new DefaultChannelGroup("Netty Unfiltered Server Channel Group", GlobalEventExecutor.INSTANCE)
    }

    override def start = {
      log.debug(s"★ ★ ★ ★ ★ ★  Start http server on $host:$httpPort  ★ ★ ★ ★ ★ ★")

      log.debug("★ ★ ★ ★ ★ ★  Index creation has been started  ★ ★ ★ ★ ★ ★")
      createIndex.runAsync {
        case \/-((\/-(index), None)) ⇒
          log.info("★ ★ ★  Index has been created  ★ ★ ★ ★ ★ ★")
          http = Option {
            unfiltered.netty.Server.bind(SocketBinding(httpPort, host))
              .handler(new HttpNettyHandler(this, index, minLenght, lenghtThreshold))
              .use(engine)
              .chunked(1048576)
              .beforeStop({
                log.debug("★ ★ ★ ★ ★ ★  Shutdown server  ★ ★ ★ ★ ★ ★")
              }).start()
          }
        case \/-((-\/(ex), None)) ⇒
          log.error(s"Error while building index: ${ex.getMessage}")
          System.exit(-1)
        case \/-((_, Some(ex))) ⇒
          log.error(s"Finalizer error while building index: ${ex.getMessage}")
          System.exit(-1)
      }
    }

    override def shutdown: Unit = http.foreach(_.stop())
  }

  @Sharable
  final class HttpNettyHandler(server: RestServer with Planner,
                               index: mutable.Map[String, RawResult],
                               minLenght: Int, lenghtThreshold: Int) extends async.Plan
      with ServerErrorResponse with AsyncContext {
    //import spray.json._
    //import DefaultJsonProtocol._

    import scalaz.stream.merge
    import scalaz.stream.csv._
    import scalaz.std.AllInstances._

    implicit val M = scalaz.Monoid[List[Result]]
    implicit val EX = scalaz.concurrent.Strategy.Executor(PlannerEx)
    val LoggerSink = scalaz.stream.sink.lift[Task, Iterable[Result]] { list ⇒ Task.delay(server.log.debug(s"output: $list")) }

    override def intent: Intent = {
      case req ⇒
        implicit val responder: unfiltered.Async.Responder[Any] = req
        implicit val ex = PlannerEx
        implicit val Codec: scala.io.Codec = scala.io.Codec.UTF8
        req match {
          case GET(Path("/info")) ⇒
            req.respond(textResponse { server.log.debug("GET /info"); server.v.toString })

          //echo '94100.00.00.072;5' | curl -d @- http://127.0.0.1:9001/orders
          //http POST http://127.0.0.1:9001/orders < ./cvs/order.csv
          case POST(Path("/orders")) ⇒
            forkTask {
              (merge.mergeN(rowsR[Order](req.inputStream, ';').map(server.respond(_, index)))(EX) observe LoggerSink)
                .map(list ⇒ scala.collection.immutable.Map(list.head.groupKey -> list))
                .foldMonoid
                .map(redistributeWithinGroup(_, lenghtThreshold, minLenght, server.log))
                .runLast.map { _.fold(errorResponse("empty"))(map ⇒ textResponse(map.mkString(";") + END)) }
            }

          case invalid ⇒
            responder.respond(errorResponse(s"Invalid request: ${invalid.method} ${invalid.uri}"))
        }
    }
  }
}