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

import com.izmeron.out.{ JsonOutputModule, OutputWriter }
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.group.DefaultChannelGroup
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.util.concurrent.GlobalEventExecutor
import unfiltered.Async
import unfiltered.netty.async.Plan.Intent
import unfiltered.netty.{ ServerErrorResponse, SocketBinding, async }
import unfiltered.request._
import unfiltered.response._

import scala.collection.mutable
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.control.NonFatal
import scala.util.{ Failure, Success }
import scalaz.concurrent.Task
import scalaz.{ -\/, \/- }

object netty {

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
      Task.fork(task)(ctx).runAsync {
        case \/-(result) ⇒ responder.respond(result)
        case -\/(error)  ⇒ responder.respond(errorResponse(error))
      }
    }

    def textResponse[A](content: String): ResponseFunction[A] = Ok ~> PlainTextContent ~> ResponseString(content + END)

    def jsonResponse[A](json: String): ResponseFunction[A] = Ok ~> JsonContent ~> ResponseString(json + END)

    def errorResponse[A](error: String): ResponseFunction[A] = BadRequest ~> PlainTextContent ~> ResponseString(error + END)

    def methodNotAllowed[A](error: String): ResponseFunction[A] = MethodNotAllowed ~> PlainTextContent ~> ResponseString(error + END)

    def errorResponse[A](error: Throwable): ResponseFunction[A] = errorResponse(error.toString)
  }

  class Server(override val indexPath: String, httpPort: Int,
               override val log: org.apache.log4j.Logger,
               override val minLenght: Int,
               override val lenghtThreshold: Int,
               val v: Version) { mixin: OrigamiAggregator ⇒

    @volatile var http: Option[unfiltered.netty.Server] = None
    private val host = unfiltered.netty.Server.allInterfacesHost

    private val engine = new unfiltered.netty.Engine {
      override val acceptor = new NioEventLoopGroup(1, new NamedThreadFactory("boss"))
      override val workers = new NioEventLoopGroup(Runtime.getRuntime.availableProcessors(), new NamedThreadFactory("http-worker"))
      override val channels = new DefaultChannelGroup("Netty Unfiltered Server Channel Group", GlobalEventExecutor.INSTANCE)
    }

    override def start() = {
      log.debug("★ ★ ★ ★ ★ ★  Index creation has been started  ★ ★ ★ ★ ★ ★")
      createIndex.runAsync {
        case \/-((\/-(index), None)) ⇒
          log.info(s"Server has been started with IndexPath: $indexPath lenghtThreshold: $lenghtThreshold minLenght: $minLenght")
          http = Option {
            unfiltered.netty.Server.bind(SocketBinding(httpPort, host))
              .handler(new HttpNettyHandler(this, index, minLenght, lenghtThreshold, log))
              .use(engine)
              .chunked(1024 * 1000 * 20)
              .beforeStop({
                log.debug("★ ★ ★ ★ ★ ★  Shutdown server  ★ ★ ★ ★ ★ ★")
              }).start()
          }
          log.debug(s"★ ★ ★ ★ ★ ★  Http server started on $host:$httpPort  ★ ★ ★ ★ ★ ★")

        case -\/(ex) ⇒
          log.error(s"Error while building index: ${ex.getMessage}")
          System.exit(-1)
        case \/-((-\/(ex), None)) ⇒
          log.error(s"Error while building index: ${ex.getMessage}")
          System.exit(-1)
        case \/-((_, Some(ex))) ⇒
          log.error(s"Finalizer error while building index: ${ex.getMessage}")
          System.exit(-1)
      }
    }

    def shutdown(): Unit = http.foreach(_.stop())
  }

  @Sharable
  final class HttpNettyHandler(server: Server with OrigamiAggregator, index: mutable.Map[String, RawResult],
                               val minLenght: Int, val lenghtThreshold: Int,
                               val log: org.apache.log4j.Logger) extends async.Plan
      with ServerErrorResponse with AsyncContext with ScalazFlowSupport {
    import OutputWriter._
    import scalaz.stream.csv._
    import scalaz.stream.{ async, merge }

    val output = OutputWriter[JsonOutputModule]

    override def intent: Intent = {
      case req ⇒
        implicit val ex = PlannerEx
        implicit val CpuIntensive = scalaz.concurrent.Strategy.Executor(PlannerEx)
        implicit val Codec: scala.io.Codec = scala.io.Codec.UTF8
        implicit val responder: unfiltered.Async.Responder[Any] = req
        req match {
          case GET(Path("/info")) ⇒
            req.respond(textResponse {
              server.log.debug("GET /info")
              server.v.toString
            })

          //echo '90300.01.061;Корпус;Сталь 40Х ф140_1225490000;Сталь 40Х;140;820;56;2;1;410;1;2;0;2' | curl -d @- http://127.0.0.1:9001/orders
          //http POST http://127.0.0.1:9001/orders < ./csv/metal2pipes2.csv
          case POST(Path("/orders")) ⇒
            forkTask {
              val queue = async.boundedQueue[List[Result]](parallelism * parallelism)
              (inputReader(rowsR[Order](req.inputStream, ';').map(server.distribute(_, index)), queue).drain merge merge.mergeN(parallelism)(cuttingStock(queue)))
                .foldMap(output.monoidMapper(lenghtThreshold, _))(output.monoid)
                .runLast
                .map {
                  _.fold(errorResponse(output.empty))(json ⇒ jsonResponse(output.convert(json)))
                }
            }

          case invalid ⇒
            responder.respond(methodNotAllowed(s"Method:${invalid.method} Uri:${invalid.uri}"))
        }
    }
  }
}
