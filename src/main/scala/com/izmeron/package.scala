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

import com.izmeron.http.AsyncContext

import scala.util.Try
import scalaz.concurrent.Task
import scalaz.{ -\/, \/-, \/ }
import scala.annotation.tailrec
import java.util.concurrent.Executors._

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

  //Fancy symbol from 1C provided file
  val cvsSpace = 160.toChar.toString

  val PlannerEx = newFixedThreadPool(Runtime.getRuntime.availableProcessors(), new NamedThreadFactory("planner"))

  case class Order(kd: String, quantity: Int)
  case class Version(major: Int, minor: Int, bf: Int)

  implicit val rowReaderOrder = RowReader(rec ⇒ Order(rec(0), rec(13).toInt))

  case class RawCsvLine(kd: String, name: String, nameMat: String, marka: String,
                        diam: String, len: String, indiam: String, numOptim: String,
                        numMin: String, lenMin: String, numSect: String, numPart: String, techComp: String)

  implicit val rowReader = RowReader(rec ⇒ RawCsvLine(rec(0), rec(1), rec(2), rec(3), rec(4), rec(5), rec(6),
    rec(7), rec(8), rec(9), rec(10), rec(11), rec(12)))

  case class Etalon(kd: String, name: String, nameMat: String, marka: String, diam: Int, len: Int, indiam: Int, qOptimal: Int, qMin: Int,
                    lenMin: Int, numSect: Int, numPart: Int, tProfit: Int)

  object Result {
    def redistribute(credit: Result, debit: Result): (Result, Result) =
      (credit.copy(cQuantity = credit.cQuantity - 1),
        debit.copy(cQuantity = debit.cQuantity + 1))
  }

  case class Result(kd: String, groupKey: String = "", length: Int = 0, cLength: Int = 0,
                    cQuantity: Int = 0, optQuantity: Int = 0, multiplicity: Int = 0, minQuant: Int = 0, profit: Int = 0)

  case class RawResult(kd: String, groupKey: String, qOptimal: Int, lenght: Int, minLenght: Int, techProfit: Int,
                       minQuantity: Int, multiplicity: Int)

  final class NamedThreadFactory(var name: String) extends ThreadFactory {
    private def namePrefix = name + "-thread"

    private val threadNumber = new AtomicInteger(1)
    private val group: ThreadGroup = Thread.currentThread().getThreadGroup

    override def newThread(r: Runnable) = new Thread(this.group, r,
      s"$namePrefix-${threadNumber.getAndIncrement()}", 0L)
  }

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

  /**
   *
   *
   *
   */
  private[izmeron] def cuttingStockProblem(group: List[Result], threshold: Int, minLenght: Int, coefficient: Double,
                                           log: org.apache.log4j.Logger): List[Combination] = {
    def unit(r: Result, ind: Int): StockUnit =
      StockUnit(ind, r.kd, r.groupKey, r.length)

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
      val gk = group.head.groupKey
      log.debug(s"CuttingStockProblem with: $group")
      val flatList = groupedMap./:(List.empty[StockUnit]) { (acc, c) ⇒
        var ind = 0
        acc ::: c._2.flatMap { r ⇒
          List.fill(r.cQuantity) {
            ind += 1
            unit(r, ind)
          }
        }
      }

      var provision: List[Provision] = Nil
      for ((k, list) ← flatList.groupBy(_.kdKey)) {
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
        if (r.stocks.nonEmpty)
          provision = r.copy(length = sumLenght) :: provision
      }

      log.debug(s"Provision: $provision")

      val lensCounts = provision./:(mutable.Map[Int, Int]().withDefaultValue(0)) { (acc, c) ⇒
        val currentLength = acc(c.length)
        if (currentLength == 0) acc += (c.length -> 1)
        else acc += (c.length -> (currentLength + 1))
        acc
      }
      val lensMapping = provision./:(mutable.Map[Int, List[String]]().withDefaultValue(Nil)) { (acc, c) ⇒
        acc(c.length) match {
          case Nil    ⇒ acc += (c.length -> List(c.kdKey))
          case h :: t ⇒ acc += (c.length -> (c.kdKey :: h :: t))
        }
        acc
      }

      val blocks = lensCounts.keySet.toArray
      val quantities = lensCounts.values.toArray

      log.debug(s"lensCounts: $lensCounts")
      log.debug(s"lenMapping: $lensMapping")

      collect(blocks, quantities, minLenght, threshold, coefficient, log, Nil).fold(List.empty[Combination]) {
        _.map { cmb ⇒
          cmb.copy(groupKey = gk, sheets =
            cmb.sheets.flatMap { sheet ⇒
              val list = lensMapping(sheet.lenght)
              if (sheet.quantity == 1) {
                lensMapping += (sheet.lenght -> list.tail)
                sheet.copy(kd = list.head) :: Nil
              } else {
                @tailrec def redistribute(n: Int, scr: List[String], acc: List[Sheet]): List[Sheet] =
                  if (n > 0) redistribute(n - 1, scr.tail, sheet.copy(kd = scr.head, quantity = 1) :: acc)
                  else acc

                lensMapping += (sheet.lenght -> list.tail.drop(sheet.quantity - 1))
                redistribute(sheet.quantity, list, Nil)
              }
            })
        }
      }
    } else Nil
  }

  private def flatQuantities(blocks: Array[Int], quantities: Array[Int]): List[Int] = {
    var buffer = List.empty[Int]
    var i = 0
    while (i < quantities.length) {
      buffer = List.fill(quantities(i))(blocks(i)) ++ buffer
      i += 1
    }
    buffer
  }

  private[izmeron] def collect(blocks: Array[Int], quantities: Array[Int],
                               minLenght: Int, sheetLength: Int, coefficient: Double,
                               log: org.apache.log4j.Logger,
                               items: List[Combination]): Option[List[Combination]] = {
    @tailrec def distributeLast(currentLen: Int, min: Int, acc: List[Sheet],
                                combination: Combination): (List[Sheet], Combination) = {
      val sheet = combination.sheets.head
      val updatedLen = currentLen + (sheet.lenght * sheet.quantity)
      if (updatedLen < min) distributeLast(updatedLen, min, Sheet(sheet.kd, sheet.lenght, sheet.quantity) :: acc,
        combination.copy(sheets = combination.sheets.tail))
      else (Sheet(sheet.kd, sheet.lenght, sheet.quantity) :: acc, combination.copy(sheets = combination.sheets.tail, rest = combination.rest + sheet.lenght))
    }

    cutNext(blocks, quantities, sheetLength, log) flatMap { cmb ⇒
      val (b, q) = crossOut(cmb)

      if (q.length > 0) {
        //distribute manually last chunks
        var i = 0
        var balanceLen = 0
        while (i < q.length) {
          balanceLen += q(i) * b(i)
          i += 1
        }

        if (q.length >= 2 && (balanceLen / q.sum) < (minLenght * coefficient)) {
          val sortedBuffer = flatQuantities(b, q).sorted
          if (sortedBuffer.sum <= sheetLength) {
            val list = sortedBuffer.map(i ⇒ Sheet(lenght = i, quantity = 1))
            Option(List(Combination(sheets = list, rest = sheetLength - list./:(0)((acc, c) ⇒ acc + c.lenght * c.quantity))))
          } else {
            Option {
              sortedBuffer.grouped(2).map { list ⇒
                list.map(i ⇒ Sheet(lenght = i, quantity = 1))
              }.map(list ⇒ Combination(sheets = list, rest = sheetLength - list./:(0)((acc, c) ⇒ acc + c.lenght * c.quantity))).toList
            }
          }
        } else if (balanceLen < minLenght) {
          val sorted = cmb._1.copy(sheets = cmb._1.sheets.flatMap { s ⇒
            List.fill(s.quantity)(Sheet(s.kd, s.lenght, 1))
          }.sortWith(_.lenght - _.lenght < 0))
          val firstPart = flatQuantities(b, q)./:(List.empty[Sheet])((acc, c) ⇒ Sheet(lenght = c, quantity = 1) :: acc)
          val (secondPart, old) = distributeLast(balanceLen, minLenght, Nil, sorted)
          val updatedSheets = firstPart ::: secondPart
          val sumLen = updatedSheets./:(0)((acc, c) ⇒ acc + c.lenght * c.quantity)
          Option(List(Combination(sheets = updatedSheets, groupKey = old.groupKey, rest = sheetLength - sumLen), old))
        } else collect(b, q, minLenght, sheetLength, coefficient, log, cmb._1 :: items)
      } else Option(cmb._1 :: items)
    }
  }

  private def crossOut(cmb: (Combination, Array[Int], Array[Int])): (Array[Int], Array[Int]) = {
    val blocks = cmb._2.toBuffer
    val quantities = cmb._3.toBuffer
    for (c ← cmb._1.sheets) {
      var ind = 0
      while (blocks(ind) != c.lenght) { //What about IndexOutOfBound
        ind += 1
      }

      val cur = quantities(ind)
      if (cur > c.quantity) {
        quantities(ind) = cur - c.quantity
      } else if (cur == c.quantity) {
        blocks.remove(ind)
        quantities.remove(ind)
      } else throw new Exception("Can't cross out more than we got")
    }
    (blocks.toArray, quantities.toArray)
  }

  case class Sheet(kd: String = "", lenght: Int = 0, quantity: Int = 0)
  case class Combination(sheets: List[Sheet] = Nil, rest: Int = 0, groupKey: String = "")

  import com.izmeron.CuttingStockProblem
  import java.util.{ Map ⇒ JMap, HashMap ⇒ JHashMap }
  private def cutNext(blocks: Array[Int], quantities: Array[Int],
                      sheetLength: Int, log: org.apache.log4j.Logger): Option[(Combination, Array[Int], Array[Int])] = {
    val quantities0 = Array.fill(quantities.length)(0)
    val blocks0 = Array.fill(blocks.length)(0)
    Array.copy(quantities, 0, quantities0, 0, quantities.length)
    Array.copy(blocks, 0, blocks0, 0, blocks.length)

    @tailrec def fetch(problem: CuttingStockProblem, result: List[Combination],
                       error: Boolean): List[Combination] = {
      if (problem.hasMoreCombinations && !error) {
        var wasError = false
        var sheets: List[Sheet] = List.empty
        val map: JMap[Integer, Integer] = Try(problem.nextBatch)
          .getOrElse {
            wasError = true; new JHashMap[Integer, Integer](1)
          }
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
        fetch(problem, Combination(sheets, sheetLength - sum) :: result, wasError)
      } else result
    }
    //
    val combinations = fetch(new CuttingStockProblem(sheetLength, blocks, quantities), Nil, error = false)
    combinations.headOption.map { head ⇒
      combinations./:(head) { (acc, c) ⇒
        if (acc == c) c
        else if (c.rest < acc.rest) c
        else if (c.sheets.size > acc.sheets.size) c
        else acc
      }
    }.map((_, blocks0, quantities0))
  }

  private[izmeron] def groupByOptimalNumber(ord: Order, threshold: Int, minLenght: Int, log: org.apache.log4j.Logger)(rr: RawResult): List[Result] = {
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

    if (buffer.nonEmpty) {
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

  private[izmeron] def distributeWithinGroup(threshold: Int, minLenght: Int, log: org.apache.log4j.Logger)(list: List[Result]): List[Result] = {
    val result = if (list.size > 1 && (list.minBy(_.cQuantity).cQuantity != list.head.optQuantity)) {
      var cnt = 0
      var ind = 0
      var min = list.minBy(_.cQuantity)
      val completed = list.filter(_ != min).toBuffer

      while (threshold - completed(ind).cLength > minLenght && min.cQuantity > 0) {
        log.debug(threshold + " - " + min + " - " + completed + "-" + min.cQuantity + "- " + ind)
        val candidate = completed(ind)
        val (credited, debited) = Result.redistribute(min, candidate)
        completed(ind) = debited
        min = credited
        cnt += 1
        ind = cnt % completed.size
      }

      if (min.cQuantity > 0) completed.+:(min).toList else completed.toList
    } else list

    if (list.nonEmpty) log.debug(s"2 - ${list.head.kd} - ${list.head.groupKey} distributeWithinGroup: $result")
    revisitSum(result)
  }

  private def revisitSum(list: List[Result]) =
    list.map { item ⇒
      if (item.cQuantity == item.optQuantity) item
      else {
        val q = if (item.cQuantity > item.optQuantity) item.cQuantity - item.optQuantity else item.cQuantity
        val sum = if (item.cQuantity > item.optQuantity) item.cLength else 0
        item.copy(cLength = sum + ((item.length - item.profit) / (item.minQuant / item.multiplicity)) *
          (q / item.multiplicity) + item.profit + ((item.minQuant / item.multiplicity) - 1) * 2 + 4)
      }
    }

  sealed trait CliCommand {
    def start(): Unit
  }

  object Exit extends CliCommand {
    override def start() = System.exit(0)
  }

  final class StaticCheck(override val path: String, override val minLenght: Int,
                          override val lenghtThreshold: Int, override val coefficient: Double) extends CliCommand with Planner {
    override val log = org.apache.log4j.Logger.getLogger("static-check")

    val ok = "Ok"
    val separator = ";"
    val rule0 = "0. Max lenght rule violation"
    val rule1 = "1. Multiplicity violation"

    override def start() = {
      maxLengthCheck.attemptRun.fold({ th: Throwable ⇒ println(s"${Ansi.errorMessage(th.getMessage)}") }, {
        case ((\/-(elem), None)) ⇒
          elem.fold(println(s"${Ansi.errorMessage("Can't perform check")}")) { maxLenElem ⇒
            if (lenghtThreshold < maxLenElem.len) println(s"${Ansi.blueMessage(rule0)}: ${Ansi.errorMessage(s"Config value $lenghtThreshold but $maxLenElem has been found")}")
            else println(s"${Ansi.blueMessage(rule0)}: ${Ansi.blueMessage(ok)}")
          }
        case ((-\/(ex)), None) ⇒ println(s"${Ansi.blueMessage(rule0)}: ${Ansi.errorMessage(ex.getMessage)}")
        case other             ⇒ println(s"${Ansi.blueMessage(rule0)}: ${Ansi.errorMessage(other.toString())}")
      })

      multiplicityCheck.attemptRun.fold({ th: Throwable ⇒ println(s"${Ansi.errorMessage(th.getMessage)}") }, {
        case ((\/-(list), None)) ⇒
          if (list.isEmpty)
            println(s"${Ansi.blueMessage(rule1)}: ${Ansi.blueMessage(ok)}")
          else
            println(s"${Ansi.blueMessage(rule1)}: ${Ansi.green(list.size.toString)}: ${Ansi.errorMessage(list.mkString(separator))}")
        case ((-\/(ex)), None) ⇒ println(s"${Ansi.blueMessage(rule1)}: ${Ansi.errorMessage(ex.getMessage)}")
        case other             ⇒ println(s"${Ansi.blueMessage(rule1)}: ${Ansi.errorMessage(other.toString())}")
      })
    }
  }

  final class Plan(override val path: String, override val minLenght: Int,
                   override val lenghtThreshold: Int, override val coefficient: Double) extends CliCommand
      with Planner with ScalazProcessSupport with AsyncContext {
    import scalaz.stream.merge

    implicit val ex = PlannerEx
    implicit val CpuIntensive = scalaz.concurrent.Strategy.Executor(PlannerEx)

    override val log = org.apache.log4j.Logger.getLogger("planner")
    private val queue = scalaz.stream.async.boundedQueue[List[Result]](parallelism * parallelism)

    override def start(): Unit = {
      createIndex.runAsync {
        case \/-((\/-(index), None)) ⇒ {
          println(Ansi.green("Index has been created"))
          Task.fork {
            (inputReader(orderReader.map(plan(_, index)), queue).drain merge merge.mergeN(parallelism)(cuttingWorkers(coefficient, queue)))
              .foldMap(jsMapper(_))(JsValueM)
              .runLast
              .map(_.fold("Empty dataset")(_.prettyPrint))
          }.runAsync {
            case \/-(result) ⇒ println(Ansi.green(result))
            case -\/(error)  ⇒ println(Ansi.red(s"${error.getClass.getName}: ${error.getStackTrace.mkString("\n")}: ${error.getMessage}"))
          }
        }
        case -\/(ex)              ⇒ println(Ansi.red(s"${ex.getClass.getName}: ${ex.getStackTrace.mkString("\n")}: ${ex.getMessage}"))
        case \/-((-\/(ex), None)) ⇒ println(Ansi.red(s"Error while building index: ${ex.getMessage}"))
        case \/-((_, Some(ex)))   ⇒ println(Ansi.red(s"Finalizer error while building index: ${ex.getMessage}"))
      }
    }
  }
}