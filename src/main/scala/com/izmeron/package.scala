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

package com

import java.util.concurrent.Executors._

import scalaz.\/

package object izmeron {
  import scala.collection.mutable
  import com.ambiata.origami.FoldM
  import com.ambiata.origami._, Origami._
  import com.nrinaudo.csv.RowReader
  import org.apache.log4j.Logger
  import java.util.concurrent.ThreadFactory
  import java.util.concurrent.atomic.AtomicInteger

  type Or = String \/ RawResult
  val empty = ""
  val cvsSpace = 160.toChar.toString

  val PlannerEx = newFixedThreadPool(Runtime.getRuntime.availableProcessors(), new NamedThreadFactory("planner"))

  case class Order(kd: String, quantity: Int)
  implicit val rowReader0 = RowReader(rec ⇒ Order(rec(0), rec(1).toInt))

  case class RawCsvLine(kd: String, name: String, nameMat: String, marka: String,
                        diam: String, len: String, indiam: String, numOptim: String,
                        numMin: String, lenMin: String, numSect: String, numPart: String, techComp: String)

  implicit val rowReader = RowReader(rec ⇒ RawCsvLine(rec(0), rec(1), rec(2), rec(3), rec(4), rec(5), rec(6),
    rec(7), rec(8), rec(9), rec(10), rec(11), rec(12)))

  case class Etalon(kd: String, name: String, nameMat: String, marka: String,
                    diam: Int, len: Int, indiam: Int, qOptimal: Int, qMin: Int,
                    lenMin: Int, numSect: Int, numPart: Int, tProfit: Int)

  object Result {
    def redistribute(credit: Result, debit: Result): (Result, Result) =
      (credit.copy(cQuantity = credit.cQuantity - 1),
        debit.copy(cQuantity = debit.cQuantity + 1))

    def redistribute2(from: Result, to: Result): (Result, Result) =
      (from.copy(cQuantity = from.cQuantity - 1, cLength = from.cLength - from.length),
        to.copy(cQuantity = to.cQuantity + 1, cLength = to.cLength + from.length))
  }

  case class Result(kd: String, groupKey: String = "",
                    length: Int = 0, cLength: Int = 0,
                    cQuantity: Int = 0, optQuantity: Int = 0,
                    multiplicity: Int = 0, minQuant: Int = 0,
                    profit: Int = 0) {
    override def toString =
      s"[$kd - $groupKey; length:$length; cLength:$cLength; cQuantity:$cQuantity; optQuantity:$optQuantity]"
  }

  case class RawResult(kd: String, groupKey: String, qOptimal: Int, lenght: Int, minLenght: Int, techProfit: Int,
                       minQuantity: Int, multiplicity: Int)

  final class NamedThreadFactory(var name: String) extends ThreadFactory {
    private def namePrefix = name + "-thread"
    private val threadNumber = new AtomicInteger(1)
    private val group: ThreadGroup = Thread.currentThread().getThreadGroup

    override def newThread(r: Runnable) = new Thread(this.group, r,
      s"$namePrefix-${threadNumber.getAndIncrement()}", 0L)
  }

  case class Version(major: Int, minor: Int, bf: Int)

  def Log4jSink: SinkM[scalaz.Id.Id, Etalon] = new FoldM[scalaz.Id.Id, Etalon, Unit] {
    private val name = "origami-fold-logger"
    override type S = org.apache.log4j.Logger

    override def fold = (state: S, elem: Etalon) ⇒ {
      state.debug(elem)
      state
    }
    override val start: scalaz.Id.Id[Logger] =
      Logger.getLogger(name)

    override def end(s: S): scalaz.Id.Id[Unit] =
      s.debug(s"$name is being completed")
  }

  def distributeWithGroup(group: collection.immutable.Map[String, List[Result]],
                          threshold: Int, minLenght: Int, log: org.apache.log4j.Logger): mutable.Map[String, List[Result]] = {
    val gMap = group.values.flatten./:(collection.mutable.Map[String, List[Result]]()) { (map, c) ⇒
      if ((c.kd ne null) && (c.kd.length > 0))
        map += (c.kd -> (c :: map.getOrElse(c.kd, List.empty[Result])))
      map
    }

    //log.debug("group:" + gMap)
    val groupedMap = collection.mutable.Map[String, List[Result]]()
    for ((k, v) ← gMap) {
      var minR = v.minBy(_.cQuantity)
      val others = v.filter(_ != minR)
      log.debug(s"minR:$minR - cQuantity:${minR.cQuantity} - optQuantity:${minR.optQuantity}")
      if (minR.cQuantity < minR.optQuantity) {
        val candidates = gMap.filterKeys(_ != k)
        for ((k0, v0) ← candidates) {
          var index = 0
          var i = 0
          var touched = false
          val completed = v0.toBuffer
          var gk: String = null.asInstanceOf[String]
          for (c ← completed) {
            if (threshold - (c.length * c.cQuantity) > minLenght && minR.cQuantity > 0) {
              log.debug(s"ind: $index - from $minR - to:${completed(index)}")
              touched = true
              gk = s"${minR.kd} - ${c.kd}"
              groupedMap += (gk -> (c :: groupedMap.getOrElse(gk, List.empty[Result])))
              groupedMap += (gk -> (minR.copy(cQuantity = 1, cLength = minR.length) :: groupedMap(gk)))
              val (reduced, increased) = Result.redistribute2(minR, c)
              minR = reduced
              completed(index) = increased
              i += 1
              index = i % completed.size
            }
          }

          if (!touched)
            groupedMap += (k0 -> (v0 ::: groupedMap.getOrElse(k, List.empty[Result])))

          //collect rest of from
          if (index % completed.size != 0)
            groupedMap += (k0 -> (completed.drop(index).toList))
        }

        //collect rest of to
        if (minR.cQuantity > 0) groupedMap += (k -> (minR :: groupedMap.getOrElse(k, List.empty[Result])))
        groupedMap += (k -> (others ::: groupedMap.getOrElse(k, List.empty[Result])))
      }
    }
    if (groupedMap.isEmpty) { log.debug("nothing to distribute"); gMap } else groupedMap
  }

  def groupByOptimalNumber(ord: Order, threshold: Int, minLenght: Int, log: org.apache.log4j.Logger)(rr: RawResult): List[Result] = {
    var buffer: List[Result] = Nil
    val map = mutable.Map[Long, List[Result]]().withDefaultValue(Nil)
    var quantity = 0
    var position = 0l

    //group by
    while (quantity < ord.quantity) {
      quantity += 1
      buffer = Result(rr.kd, rr.groupKey, rr.minLenght, rr.minLenght, 1, rr.qOptimal,
        rr.multiplicity, rr.minQuantity, rr.techProfit) :: buffer
      if (quantity % rr.qOptimal == 0) {
        position += 1
        map += (position -> buffer)
        buffer = Nil
      }
    }

    if (buffer.size > 0) {
      position += 1
      map += (position -> buffer)
    }

    val result = (for ((k, group) ← map) yield {
      val q = group.size
      group.reduce { (l, r) ⇒
        Result(l.kd, l.groupKey, l.length, l.cLength + r.cLength, q, l.optQuantity,
          l.multiplicity, l.minQuant, l.profit)
      }
    }).toList

    log.debug(s"1 - ${rr.kd} - ${rr.groupKey} groupByOptimalNumber: $result")
    result
  }

  def distributeWithinGroup(threshold: Int, minLenght: Int, log: org.apache.log4j.Logger)(list: List[Result]): List[Result] = {
    val result = if (list.size > 1 && (list.minBy(_.cQuantity).cQuantity != list.head.optQuantity)) {
      var cnt = 0
      var ind = 0
      var min = list.minBy(_.cQuantity)
      val completed = list.filter(_ != min).toBuffer

      while (threshold - completed(ind).cLength > minLenght && min.cQuantity > 0) {
        val candidate = completed(cnt)
        val (credited, debited) = Result.redistribute(min, candidate)
        completed(cnt) = debited
        min = credited
        cnt += 1
        ind = cnt % completed.size
      }

      if (min.cQuantity > 0) (completed.+:(min)).toList else completed.toList
    } else list

    if (list.size > 0) log.debug(s"2 - ${list.head.kd} - ${list.head.groupKey} distributeWithinGroup: $result")
    revisitSum(result)
  }

  def revisitSum(list: List[Result]) =
    list.map { item ⇒
      if (item.cQuantity == item.optQuantity) item
      else {
        val q = if (item.cQuantity > item.optQuantity) item.cQuantity - item.optQuantity else item.cQuantity
        val sum = if (item.cQuantity > item.optQuantity) item.cLength else 0
        item.copy(cLength = sum + ((item.length - item.profit) / (item.minQuant / item.multiplicity)) *
          (q / item.multiplicity) + item.profit + ((item.minQuant / item.multiplicity) - 1) * 2 + 4)
      }
    }
}