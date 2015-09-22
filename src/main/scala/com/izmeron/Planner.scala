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
  import com.izmeron._
  import com.ambiata.origami._, Origami._
  import com.ambiata.origami.stream.FoldableProcessM._
  import scala.collection.mutable
  import com.ambiata.origami.FoldM
  import com.ambiata.origami._, Origami._
  import com.ambiata.origami.effect.FinalizersException
  import scalaz.{ \/, \/-, -\/ }
  import scalaz.concurrent.Task
  import scalaz.stream.Process

  def log: org.apache.log4j.Logger

  def path: String

  def lenghtThreshold: Int

  def minLenght: Int

  def start(): Unit

  def shutdown(): Unit

  private def csvSource: scalaz.stream.Process[Task, Etalon] =
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
    }

  private def buildIndex: FoldM[SafeTTask, Etalon, mutable.Map[String, RawResult]] =
    fromFoldLeft[Etalon, mutable.Map[String, RawResult]](mutable.Map[String, RawResult]()) { (acc, c) ⇒
      val key = s"${c.marka}/${c.diam}/${c.indiam}"
      acc += (c.kd -> RawResult(c.kd, key, c.qOptimal, c.len, c.lenMin, c.tProfit, c.qMin, c.numSect))
      acc
    }.into[SafeTTask]

  def createIndex: Task[(Throwable \/ mutable.Map[String, RawResult], Option[FinalizersException])] =
    Task.fork((buildIndex run csvSource).attemptRun)(PlannerEx)

  def respond(ord: com.izmeron.Order, index: mutable.Map[String, RawResult]): scalaz.stream.Process[Task, List[Result]] =
    (Process.await(Task.delay(index.get(ord.kd))) { values: Option[RawResult] ⇒
      values.fold(Process.emit(-\/(ord.kd): Or)) { result: RawResult ⇒ Process.emit(\/-(result): Or) }
    }).flatMap {
      _.fold({ kd ⇒
        log.error(s"Can't find kd:[$kd] in current index")
        Process.emit(List(Result(ord.kd)))
      }, { seq ⇒
        Process.emit(groupByOptimalNumber(ord, lenghtThreshold, minLenght, log)(seq))
          .map { list: List[Result] ⇒ redistributeWithin(lenghtThreshold, minLenght, log)(list) }
      })
    }
}
