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

import scala.annotation.tailrec
import scala.collection.mutable.ArrayBuffer
import scala.util.Try
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

  case class StockUnit(id: Int, kdKey: String, group: String, length: Int)

  case class Provision(kdKey: String = "", length: Int = 0, stocks: List[Int] = Nil)

  private def create(r: Result, ind: Int): StockUnit = StockUnit(ind, r.kd, r.groupKey, r.length)

  def cuttingStockProblem(group: List[Result], threshold: Int, minLenght: Int,
                          log: org.apache.log4j.Logger): List[Combination] = {
    val gk = group.head.groupKey
    val groupedMap = group./:(collection.mutable.Map[String, List[Result]]()) { (map, c) ⇒
      if ((c.kd ne null) && (c.kd.length > 0))
        map += (c.kd -> (c :: map.getOrElse(c.kd, List.empty[Result])))
      map
    }

    if (groupedMap.keySet.size == 1) {
      log.debug(s"Simple grouping with: $group")
      val list = groupedMap.values.iterator.next()
      list./:(List.empty[Combination]) { (acc, c) ⇒
        var ind = 0
        Combination(groupKey = c.groupKey,
          sheets = List.fill(c.cQuantity) {
            ind += 1
            Sheet(c.kd, c.length, 1)
          },
          rest = threshold - c.cLength) :: acc
      }
    } else if (groupedMap.keySet.size >= 2) {
      log.debug(s"CuttingStockProblem with: $group")
      val expanded = groupedMap./:(List.empty[StockUnit]) { (acc, c) ⇒
        var ind = 0
        acc ::: c._2.flatMap { r ⇒
          List.fill(r.cQuantity) {
            ind += 1
            create(r, ind)
          }
        }
      }
      log.debug(s"Expanded with: $expanded")

      var provision: List[Provision] = Nil
      for ((k, list) ← expanded.groupBy(_.kdKey)) {
        var r = Provision()
        var sumLenght = 0
        for (el ← list) {
          sumLenght += el.length
          if (sumLenght < minLenght) {
            r = r.copy(el.kdKey, r.length, el.id :: r.stocks)
          } else {
            provision = r.copy(el.kdKey, sumLenght, el.id :: r.stocks) :: provision
            sumLenght = 0
            r = Provision()
          }
        }
        if (r.stocks.headOption.isDefined)
          provision = r.copy(length = sumLenght) :: provision
      }
      log.debug(s"Provision: $provision")

      val lensCounts = provision./:(mutable.Map[Int, Int]().withDefaultValue(0)) { (acc, c) ⇒
        val currentLength = acc(c.length)
        if (currentLength == 0) acc += (c.length -> 1)
        else acc += (c.length -> (currentLength + 1))
        acc
      }
      val lenMapping = provision./:(mutable.Map[Int, List[String]]().withDefaultValue(Nil)) { (acc, c) ⇒
        acc(c.length) match {
          case Nil    ⇒ acc += (c.length -> List(c.kdKey))
          case h :: t ⇒ acc += (c.length -> (c.kdKey :: h :: t))
        }
        acc
      }

      val blocks = lensCounts.keySet.toArray
      val quantities = lensCounts.values.toArray

      log.debug(s"LensCounts: $lensCounts")
      log.debug(s"lenMapping: $lenMapping")

      collect(blocks, quantities, threshold, log, Nil).fold(List.empty[Combination]) {
        _.map { cmb ⇒
          cmb.copy(groupKey = gk, sheets =
            cmb.sheets.map { sheet ⇒
              val list = lenMapping(sheet.lenght)
              if (sheet.quantity == 1)
                lenMapping += (sheet.lenght -> list.tail)
              else
                lenMapping += (sheet.lenght -> list.tail.drop(sheet.quantity - 1))
              sheet.copy(kd = list.head)
            })
        }
      }
    } else Nil
  }

  def collect(blocks: Array[Int], quantities: Array[Int],
              sheetLength: Int, log: org.apache.log4j.Logger,
              items: List[Combination]): Option[List[Combination]] = {
    cutNext(blocks, quantities, sheetLength, log) flatMap { cmb ⇒
      val (b, q) = crossOut(cmb)
      if (q.length > 0) {

        //artificial part
        if (q.length == 2 && (b(0) + b(0)) <= sheetLength) {
          val artificial = Combination(List(Sheet(lenght = b(0), quantity = 1), Sheet(lenght = b(1), quantity = 1)), sheetLength - (b(0) + b(1)))
          val (bA, qA) = crossOut((artificial, b, q))
          collect(bA, qA, sheetLength, log, artificial :: cmb._1 :: items)
        } else collect(b, q, sheetLength, log, cmb._1 :: items)
      } else Option(cmb._1 :: items)
    }
  }

  def crossOut(cmb: (Combination, Array[Int], Array[Int])): (Array[Int], Array[Int]) = {
    val blocks = cmb._2.toBuffer
    val quantities = cmb._3.toBuffer
    for (c ← cmb._1.sheets) {
      var ind = 0
      while (blocks(ind) != c.lenght) { //IndexOutOfBound
        ind += 1
      }

      val cur = quantities(ind)
      if (cur > c.quantity) {
        quantities(ind) = cur - c.quantity
      } else if (cur == c.quantity) {
        blocks.remove(ind)
        quantities.remove(ind)
      } else throw new Exception("Can't cross out more than we have") //error
    }
    (blocks.toArray, quantities.toArray)
  }

  case class Sheet(kd: String = "", lenght: Int = 0, quantity: Int = 0)
  case class Combination(sheets: List[Sheet] = Nil, rest: Int = 0, groupKey: String = "")

  import com.izmeron.CuttingStockProblem
  import java.util.{ Map ⇒ JMap, HashMap ⇒ JHashMap }
  def cutNext(blocks: Array[Int], quantities: Array[Int],
              sheetLength: Int, log: org.apache.log4j.Logger): Option[(Combination, Array[Int], Array[Int])] = {

    val quantities0 = Array.fill(quantities.size)(0)
    val blocks0 = Array.fill(blocks.size)(0)
    Array.copy(quantities, 0, quantities0, 0, quantities.size)
    Array.copy(blocks, 0, blocks0, 0, blocks.size)

    @tailrec def loop(problem: CuttingStockProblem, result: List[Combination],
                      error: Boolean): List[Combination] =
      if (problem.hasMoreCombinations && !error) {
        var wasError = false
        var sheets: List[Sheet] = List.empty
        val map: JMap[Integer, Integer] = Try(problem.nextBatch)
          .getOrElse { wasError = true; new JHashMap[Integer, Integer](1) }
        val iter = map.entrySet.iterator
        var sum = 0
        var k = 0
        var v = 0
        while (iter.hasNext) {
          val next = iter.next
          k = next.getKey
          v = next.getValue
          sum += k * v
          sheets = Sheet(lenght = k, quantity = v) :: sheets
        }
        loop(problem, Combination(sheets, sheetLength - sum) :: result, wasError)
      } else result

    val combinations = loop(new CuttingStockProblem(sheetLength, blocks, quantities), Nil, false)
    combinations.headOption.map { head ⇒
      combinations./:(head) { (acc, c) ⇒
        if (acc == c) c
        else if (c.rest < acc.rest) c
        else if (c.sheets.size > acc.sheets.size) c
        else acc
      }
    }.map((_, blocks0, quantities0))
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