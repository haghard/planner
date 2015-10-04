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

//https://partialflow.wordpress.com

package com.izmeron

import java.nio.charset.StandardCharsets
import com.izmeron.out.{ OutputWriter, JsonOutputModule }
import org.http4s.dsl._
import org.http4s.server.HttpService
import com.izmeron.http._
import scala.collection.mutable
import scalaz.concurrent.Task
import scalaz.stream.{ async, merge }

object OrderService {
  import OutputWriter._
  import scalaz.stream.csv.rowsR

  val P = scalaz.stream.Process

  implicit val ex = PlannerEx
  implicit val CpuIntensive = scalaz.concurrent.Strategy.Executor(PlannerEx)
  implicit val Codec: scala.io.Codec = scala.io.Codec.UTF8

  private val sep = ';'
  private var lenghtThreshold = 0
  private var logger: org.apache.log4j.Logger = _
  private val writer = OutputWriter[JsonOutputModule]
  private var index = mutable.Map[String, RawResult]()
  private var aggregator: OrigamiAggregator with ScalazFlowSupport = _
  private val decodeUtf = scodec.stream.decode.many(scodec.codecs.utf8)

  def apply(aggregator: OrigamiAggregator with ScalazFlowSupport,
            index: mutable.Map[String, RawResult],
            lenghtThreshold: Int, log: org.apache.log4j.Logger): HttpService = {
    this.aggregator = aggregator
    this.index = index
    this.lenghtThreshold = lenghtThreshold
    this.logger = log
    service
  }

  //echo '94100.00.00.072;5' | curl -d @- http://127.0.0.1:9001/orders
  //http POST http://127.0.0.1:9001/orders < ./csv/metal2pipes2.csv --stream
  private val service = HttpService {
    case req @ POST -> Root / "orders" ⇒
      val queue = async.boundedQueue[List[Result]](parallelism * parallelism)
      val flow =  for {
        bv <- req.body.map(_.toBitVector)
        lines <- decodeUtf decode bv
        src = rowsR[Order](new java.io.ByteArrayInputStream(lines.getBytes(StandardCharsets.UTF_8)), sep).map(aggregator.lookupFromIndex(_, index))
        out <- (aggregator.inputReader(src, queue).drain merge merge.mergeN(parallelism)(aggregator.cuttingWorkers(queue)))
          .map { list ⇒ s"${writer.monoidMapper(lenghtThreshold, list).prettyPrint}\n" }
          .onFailure { th ⇒ P.emit(s"{ Error: ${th.getClass.getName}: ${th.getMessage}}") }
      } yield out
      Ok(flow).chunked
  }

  private def lines(iter: Iterator[String]) = {
    def loop(iter: Iterator[String]): scalaz.stream.Process[Task, String] = {
      scalaz.stream.Process.await(Task.delay(iter)) { iter ⇒
        if (iter.hasNext) {
          val line = s"${iter.next}\n"
          scalaz.stream.Process.emit(line) ++ loop(iter)
        } else scalaz.stream.Process.halt
      }
    }
    loop(iter)
  }
}