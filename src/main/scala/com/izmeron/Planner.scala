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

trait Planner {
  import com.ambiata.origami._, Origami._
  import com.ambiata.origami.stream.FoldableProcessM._
  import scala.collection.mutable
  import com.ambiata.origami.FoldM
  import com.ambiata.origami._, Origami._
  import com.ambiata.origami.effect.FinalizersException
  import scalaz.{ \/, \/-, -\/ }
  import scalaz.concurrent.Task
  import scalaz.stream.Process
  import scalaz.std.AllInstances._

  def log: org.apache.log4j.Logger

  def path: String

  def lenghtThreshold: Int

  def minLenght: Int

  def csvProcess: scalaz.stream.Process[Task, Etalon]

  def start(): Unit

  def shutdown(): Unit

  def foldCount_Plus: FoldM[SafeTTask, Etalon, (Int, Int)] =
    ((count[Etalon] observe Log4jSink) <*> plusBy[Etalon, Int](_.diam)).into[SafeTTask]

  //import com.ambiata.origami.stream.FoldableProcessM._

  private def groupBy3: FoldM[SafeTTask, Etalon, Map[String, Int]] =
    fromMonoidMap { line: Etalon ⇒ Map(s"${line.marka}/${line.diam}/${line.indiam}" -> 1) }
      .into[SafeTTask]

  private def groupBy3_0: FoldM[SafeTTask, Etalon, mutable.Map[String, Int]] =
    fromFoldLeft[Etalon, mutable.Map[String, Int]](mutable.Map[String, Int]().withDefaultValue(0)) { (acc, c) ⇒
      val key = s"${c.marka}/${c.diam}/${c.indiam}"
      acc += (key -> (acc(key) + 1))
      acc
    }.into[SafeTTask]

  private def buildIndex: FoldM[SafeTTask, Etalon, mutable.Map[String, RawResult]] =
    fromFoldLeft[Etalon, mutable.Map[String, RawResult]](mutable.Map[String, RawResult]()) { (acc, c) ⇒
      val key = s"${c.marka}/${c.diam}/${c.indiam}"
      acc += (c.kd -> RawResult(c.kd, key, c.qOptimal, c.len, c.lenMin, c.tProfit, c.qMin, c.numSect))
      acc
    }.into[SafeTTask]

  def createIndex: Task[(Throwable \/ mutable.Map[String, RawResult], Option[FinalizersException])] =
    Task.fork((buildIndex run csvProcess).attemptRun)(PlannerEx)

  /**
   *
   * @param ord
   * @param index
   * @return
   */
  def plan(ord: com.izmeron.Order, index: mutable.Map[String, RawResult]): scalaz.stream.Process[Task, List[Result]] =
    (Process.await(Task.delay(index.get(ord.kd))) { values: Option[RawResult] ⇒
      values.fold(Process.emit(-\/(ord.kd): Or)) { result: RawResult ⇒ Process.emit(\/-(result): Or) }
    }).flatMap {
      _.fold({ kd ⇒
        log.error(s"Can't find kd:[$kd] in current index")
        Process.emit(List(Result(ord.kd)))
      }, { seq ⇒
        Process.emit(groupByOptimalNumber(ord, lenghtThreshold, minLenght, log)(seq))
          .map { list: List[Result] ⇒ distributeWithinGroup(lenghtThreshold, minLenght, log)(list) }
      })
    }
}