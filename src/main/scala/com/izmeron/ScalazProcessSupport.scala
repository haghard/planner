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

import scalaz.concurrent.Task

trait ScalazProcessSupport {
  import spray.json._
  import scala.collection._
  import scalaz.stream.merge
  import scalaz.stream.Process
  import scalaz.stream.async
  import scalaz.std.AllInstances._

  def minLenght: Int
  def lenghtThreshold: Int
  def log: org.apache.log4j.Logger

  val parallelism = Runtime.getRuntime.availableProcessors()

  val loggerSink = scalaz.stream.sink.lift[Task, Iterable[Result]] { list ⇒
    Task.delay(log.debug(s"order-line: $list"))
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

  /**
   *
   * @param coefficient
   * @param queue
   * @return
   */
  def cuttingWorkers(coefficient: Double, queue: async.mutable.Queue[List[Result]]): Process[Task, Process[Task, List[Combination]]] =
    Process.range(0, parallelism)
      .map(_ ⇒ queue.dequeue.map(cuttingStockProblem(_, lenghtThreshold, minLenght, coefficient, log)))

  /**
   *
   * @param queue
   * @param S
   * @return
   */
  def inputReader(src: scalaz.stream.Process[Task, scalaz.stream.Process[Task, List[Result]]],
                  queue: async.mutable.Queue[List[Result]])(implicit S: scalaz.concurrent.Strategy): Process[Task, Unit] =
    (merge.mergeN(parallelism)(src)(S) observe loggerSink)
      .map { list ⇒ list.headOption.fold(immutable.Map[String, List[Result]]())(head ⇒ immutable.Map(head.groupKey -> list)) }
      .foldMonoid
      .flatMap { map ⇒ queuePublisher(map.values.iterator) to queue.enqueue }
      .onComplete(scalaz.stream.Process.eval_ { log.debug("All input has been scheduled"); queue.close })
}