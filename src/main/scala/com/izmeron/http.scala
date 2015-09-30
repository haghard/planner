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
import unfiltered.netty.{ ReceivedMessage, SocketBinding, ServerErrorResponse, async }
import unfiltered.request._
import unfiltered.response._

import scala.collection.mutable
import scala.concurrent.{ ExecutionContext, Future }
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

    def methodNotAllowed[A](error: String): ResponseFunction[A] = MethodNotAllowed ~> PlainTextContent ~> ResponseString(error + END)

    def errorResponse[A](error: Throwable): ResponseFunction[A] = errorResponse(error.toString)
  }

  class PlannerServer(override val path: String, httpPort: Int,
                      override val log: org.apache.log4j.Logger,
                      override val minLenght: Int,
                      override val lenghtThreshold: Int,
                      override val coefficient: Double,
                      val v: Version) { mixin: Planner ⇒

    @volatile var http: Option[unfiltered.netty.Server] = None
    private val host = unfiltered.netty.Server.allInterfacesHost

    private val engine = new unfiltered.netty.Engine {
      override val acceptor = new NioEventLoopGroup(1, new NamedThreadFactory("boss"))
      override val workers = new NioEventLoopGroup(Runtime.getRuntime.availableProcessors() * 2, new NamedThreadFactory("worker"))
      override val channels = new DefaultChannelGroup("Netty Unfiltered Server Channel Group", GlobalEventExecutor.INSTANCE)
    }

    override def start() = {
      log.debug("★ ★ ★ ★ ★ ★  Index creation has been started  ★ ★ ★ ★ ★ ★")
      createIndex.runAsync {
        case \/-((\/-(index), None)) ⇒
          log.info("★ ★ ★  Index has been created  ★ ★ ★ ★ ★ ★")
          http = Option {
            unfiltered.netty.Server.bind(SocketBinding(httpPort, host))
              .handler(new HttpNettyHandler(this, index, minLenght, lenghtThreshold, coefficient))
              .use(engine)
              .chunked(1048576)
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

    override def shutdown(): Unit = http.foreach(_.stop())
  }

  @Sharable
  final class HttpNettyHandler(server: PlannerServer with Planner,
                               index: mutable.Map[String, RawResult],
                               minLenght: Int, lenghtThreshold: Int, coefficient: Double) extends async.Plan
      with ServerErrorResponse with AsyncContext {
    import spray.json._
    import scala.collection._
    import scalaz.stream.merge
    import scalaz.stream.csv._
    import scalaz.stream.Process
    import scalaz.std.AllInstances._

    val limit = Runtime.getRuntime.availableProcessors()
    val loggerSink = scalaz.stream.sink.lift[Task, Iterable[Result]] { list ⇒
      Task.delay(server.log.debug(s"order-line: $list"))
    }

    val jsMapper = { cs: List[Combination] ⇒
      val init = JsObject("group" -> JsString(cs.head.groupKey), "body" -> JsArray())
      val writerSheet = new spray.json.JsonWriter[Sheet] {
        override def write(s: Sheet): JsValue =
          JsObject("kd" -> JsString(s.kd), "lenght" -> JsNumber(s.lenght), "quantity" -> JsNumber(s.quantity))
      }
      cs./:(init) { (acc, c) ⇒
        val cur = JsObject(
          "sheet" -> JsArray(c.sheets.toVector.map(writerSheet.write)),
          "balance" -> JsNumber(c.rest),
          "lenght" -> JsNumber(lenghtThreshold - c.rest)
        )
        JsObject(
          "group" -> acc.fields("group"),
          "body" -> JsArray(acc.fields("body").asInstanceOf[JsArray].elements.:+(cur))
        )
      }
    }

    implicit val JsValueM = new scalaz.Monoid[JsObject] {
      override def zero = JsObject("uri" -> JsString("/orders"), "body" -> JsArray())
      override def append(f1: JsObject, f2: ⇒ JsObject): JsObject = {
        (f1, f2) match {
          case (f1: JsObject, f2: JsValue) ⇒
            JsObject("uri" -> f1.fields("uri").asInstanceOf[JsString],
              "body" -> JsArray(f1.fields("body").asInstanceOf[JsArray].elements.:+(f2)))
        }
      }
    }

    def queuePublisher(it: Iterator[List[Result]]): Process[Task, List[Result]] = {
      def go(iter: Iterator[List[Result]]): Process[Task, List[Result]] =
        Process.await(Task.delay(iter)) { iter ⇒
          if (iter.hasNext) Process.emit(iter.next) ++ go(iter)
          else Process.halt
        }
      go(it)
    }

    def workers(coefficient: Double, queue: scalaz.stream.async.mutable.Queue[List[Result]]): Process[Task, Process[Task, List[Combination]]] =
      Process.range(0, limit).map(_ ⇒ queue.dequeue.map(cuttingStockProblem(_, lenghtThreshold, minLenght, coefficient, server.log)))

    def inputReader(queue: scalaz.stream.async.mutable.Queue[List[Result]],
                    req: HttpRequest[ReceivedMessage])(implicit S: scalaz.concurrent.Strategy): Process[Task, Unit] =
      (merge.mergeN(limit)(rowsR[Order](req.inputStream, ';').map(server.plan(_, index)))(S) observe loggerSink)
        .map { list ⇒ list.headOption.fold(immutable.Map[String, List[Result]]())(head ⇒ immutable.Map(head.groupKey -> list)) }
        .foldMonoid
        .flatMap { map ⇒ queuePublisher(map.values.iterator) to queue.enqueue }
        .onComplete(scalaz.stream.Process.eval_ { server.log.debug("All input has been scheduled"); queue.close })

    /**
     *
     *
     * @return
     */
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
              val queue = scalaz.stream.async.boundedQueue[List[Result]](limit * limit)
              (inputReader(queue, req).drain merge merge.mergeN(limit)(workers(coefficient, queue)))
                .foldMap(jsMapper(_))(JsValueM)
                .runLast
                .map { _.fold(errorResponse("empty response"))(json ⇒ jsonResponse(json.prettyPrint)) }
            }

          case invalid ⇒
            responder.respond(methodNotAllowed(s"Method:${invalid.method} Uri:${invalid.uri}"))
        }
    }
  }
}