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

  def path: String

  def minLenght: Int

  def lenghtThreshold: Int

  def log: org.apache.log4j.Logger

  implicit val Codec: scala.io.Codec = scala.io.Codec.UTF8

  def indexReader: scalaz.stream.Process[Task, Etalon] =
    scalaz.stream.csv.rowsR[RawCsvLine](path, ';').map { raw ⇒
      Etalon(raw.kd, raw.name, raw.nameMat, raw.marka,
        raw.diam.replaceAll(cvsSpace, empty).toInt,
        raw.len.replaceAll(cvsSpace, empty).toInt,
        raw.indiam.replaceAll(cvsSpace, empty).toInt,
        raw.numOptim.replaceAll(cvsSpace, empty).toInt,
        raw.numMin.replaceAll(cvsSpace, empty).toInt,
        raw.lenMin.replaceAll(cvsSpace, empty).toInt,
        raw.numSect.replaceAll(cvsSpace, empty).toInt,
        raw.numPart.replaceAll(cvsSpace, empty).toInt,
        raw.techComp.replaceAll(cvsSpace, empty).toInt)
    }.onFailure { th ⇒ log.debug(s"IndexReader Failure: ${th.getMessage}"); Process.halt }

  def orderReader: scalaz.stream.Process[Task, Order] =
    scalaz.stream.csv.rowsR[Order](path, ';')
      .onFailure { th ⇒ log.debug(s"OrderReader Failure: ${th.getMessage}"); Process.halt }

  def start(): Unit

  private[izmeron] def maxLen: FoldM[SafeTTask, Etalon, Option[Etalon]] =
    maximumBy[Etalon, Int] { e: Etalon ⇒ e.len }.into[SafeTTask]

  private[izmeron] def maxLenOfUnit: FoldM[SafeTTask, Etalon, Option[Etalon]] =
    maximumBy[Etalon, Int] { e: Etalon ⇒ e.lenMin }.into[SafeTTask]

  private[izmeron] def highs: FoldM[SafeTTask, Etalon, (Option[Etalon], Option[Etalon])] =
    maxLen <*> maxLenOfUnit

  private[izmeron] def foldCount_Plus: FoldM[SafeTTask, Etalon, (Int, Int)] =
    ((count[Etalon] observe Log4jSink) <*> plusBy[Etalon, Int](_.diam)).into[SafeTTask]

  private[izmeron] def groupBy3: FoldM[SafeTTask, Etalon, Map[String, Int]] =
    fromMonoidMap { line: Etalon ⇒ Map(s"${line.marka}/${line.diam}/${line.indiam}" -> 1) }
      .into[SafeTTask]

  private[izmeron] def groupByKey: FoldM[SafeTTask, Etalon, Map[String, String]] =
    fromMonoidMap { line: Etalon ⇒ Map(s"${line.marka}/${line.diam}/${line.indiam}" -> s"${line.kd}-") }
      .into[SafeTTask]

  private[izmeron] def groupByKeyMinLen: FoldM[SafeTTask, Etalon, Map[String, String]] =
    fromMonoidMap { line: Etalon ⇒ Map(s"${line.marka}/${line.diam}/${line.indiam}" -> s"${line.lenMin}-") }
      .into[SafeTTask]

  private[izmeron] def groupBy3_0: FoldM[SafeTTask, Etalon, mutable.Map[String, Int]] =
    fromFoldLeft[Etalon, mutable.Map[String, Int]](mutable.Map[String, Int]().withDefaultValue(0)) { (acc, c) ⇒
      val key = s"${c.marka}/${c.diam}/${c.indiam}"
      acc += (key -> (acc(key) + 1))
      acc
    }.into[SafeTTask]

  private[izmeron] def buildIndex: FoldM[SafeTTask, Etalon, mutable.Map[String, RawResult]] =
    fromFoldLeft[Etalon, mutable.Map[String, RawResult]](mutable.Map[String, RawResult]()) { (acc, c) ⇒
      val key = s"${c.marka}/${c.diam}/${c.indiam}"
      acc += (c.kd -> RawResult(c.kd, key, c.qOptimal, c.len, c.lenMin, c.tProfit, c.qMin, c.numSect))
      acc
    }.into[SafeTTask]

  private[izmeron] def multiplicity: FoldM[SafeTTask, Etalon, List[String]] =
    fromFoldLeft[Etalon, List[String]](List[String]()) { (acc, c) ⇒
      val counted = c.lenMin * c.numPart
      if (c.len != counted) s"[${c.kd}: Expected:$counted - Actual:${c.len}]" :: acc
      else acc
    }.into[SafeTTask]

  def maxLengthCheck: Task[(Throwable \/ Option[Etalon], Option[FinalizersException])] =
    Task.fork((maxLen run indexReader).attemptRun)(PlannerEx)

  def multiplicityCheck: Task[(Throwable \/ List[String], Option[FinalizersException])] =
    Task.fork((multiplicity run indexReader).attemptRun)(PlannerEx)

  def createIndex: Task[(Throwable \/ mutable.Map[String, RawResult], Option[FinalizersException])] =
    Task.fork((buildIndex run indexReader).attemptRun)(PlannerEx)

  def plan(ord: com.izmeron.Order, index: mutable.Map[String, RawResult]): scalaz.stream.Process[Task, List[Result]] =
    Process.await(Task.delay(index.get(ord.kd))) { values: Option[RawResult] ⇒
      values.fold(Process.emit(-\/(ord.kd): Or)) { result: RawResult ⇒ Process.emit(\/-(result): Or) }
    }.flatMap {
      _.fold({ kd ⇒
        log.error(s"Can't find kd:[$kd] in current index")
        Process.emit(List.empty[Result])
      }, { seq ⇒
        Process.emit(groupByOptimalNumber(ord, lenghtThreshold, minLenght, log)(seq))
          .map { list: List[Result] ⇒ distributeWithinGroup(lenghtThreshold, minLenght, log)(list) }
      })
    }
}