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

import com.izmeron.out.{ OutputWriter, JsonOutputModule }
import org.http4s.dsl._
import org.http4s.server.HttpService
import com.izmeron.http._
import scala.annotation.tailrec
import scala.collection.mutable
import scalaz._
import scalaz.concurrent.Task
import scalaz.stream._

object OrderService {
  import OutputWriter._

  val P = scalaz.stream.Process

  def stateScan[S, A, B](init: S)(f: A ⇒ State[S, B]): Process1[A, B] = {
    P.await1[A] flatMap { a ⇒
      val (s, b) = f(a) run init
      P.emit(b) ++ stateScan(s)(f)
    }
  }

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

  val parser = (batch: String) ⇒
    parse(batch.split("\\n").iterator, Nil)

  @tailrec private def parse(lines: Iterator[String], acc: List[Order]): (String, List[Order]) = {
    if (lines.hasNext) {
      val cur = lines.next()
      val fields = cur.split(";")
      if (fields.length == 14) parse(lines, Order(fields(0), fields(13).toInt) :: acc)
      else (cur, acc)
    } else ("", acc)
  }

  //echo '94100.00.00.072;5' | curl -d @- http://127.0.0.1:9001/orders
  //http POST http://127.0.0.1:9001/orders < ./csv/metal2pipes2.csv --stream
  private val service = HttpService {
    case req @ POST -> Root / "orders" ⇒
      val queue = async.boundedQueue[List[Result]](Math.pow(2, parallelism).toInt)
      /**
       * Computation graph
       *
       * File            Parallel stage                                  Parallel stage
       * +----------+   +-----------+                                    +------------+   +------------+
       * |csv_line0 |---|distribute |--+                            +----|cuttingStock|---|monoidMapper|
       * +----------+   +-----------+  |  Fan-in stage              |    +------------+   +------------+
       * +----------+   +-----------+  |  +----------+  +-------+   |    +------------+   +------------+
       * |csv_line1 |---|distribute |-----|foldMonoid|--| queue |--------|cuttingStock|---|monoidMapper|
       * +----------+   +-----------+  |  +----------+  +-------+   |    +------------+   +------------+
       *                               |                            |    +------------+   +------------+
       * +----------+   +-----------+  |                            +----|cuttingStock|---|monoidMapper|
       * |csv_line2 |---|distribute |--+                                 +------------+   +------------+
       * +----------+   +-----------+
       */

      def folder = (stateScan[String, String, List[Order]]("") { batch: String ⇒
        for {
          acc ← State.get[String]
          //_ = log.debug(s"prev: $acc: ${acc.length} \n $batch")
          r = if (acc.length > 0) parser(acc + batch) else parser(batch)
          _ ← State.put(r._1)
        } yield r._2
      }).flatMap(P.emitAll).map(aggregator.distribute(_, index))

      val start = System.currentTimeMillis()
      val src: Process[Task, Process[Task, List[Result]]] = (req.body.flatMap(bVector ⇒ decodeUtf.decode(bVector.toBitVector)) pipe folder)
      val graph = (aggregator.sourceToQueue(src, queue).drain merge merge.mergeN(parallelism)(aggregator.cuttingStock(queue))(CpuIntensive))
        .map { list ⇒ s"${writer.monoidMapper(lenghtThreshold, list).prettyPrint}\n" } ++ P.emit(s"""{ "latency": ${System.currentTimeMillis - start} }""")
        .onFailure { th ⇒
          logger.error(s"{ Error: ${th.getClass.getName}: ${th.getMessage}}")
          P.emit(s"{ Error: ${th.getClass.getName}: ${th.getMessage}}")
        }

      Ok(graph).chunked
  }
}