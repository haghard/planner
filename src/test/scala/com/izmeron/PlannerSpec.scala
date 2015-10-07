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

import com.izmeron._
import org.specs2.mutable.Specification

import scala.annotation.tailrec
import scalaz.State
import scalaz.concurrent.Task

class PlannerSpec extends Specification {
  var lenghtThreshold = 1400
  val minLenght = 400

  val logger = org.apache.log4j.Logger.getLogger("test-planner")

  "cuttingStockProblem" should {
    "scenario0" in {
      val in =
        Result("86401.095", "Сталь 20Х/120/56", 614, 1228, 2, 2, 1, 1, 0) ::
          Result("86602.056", "Сталь 20Х/120/56", 275, 825, 3, 3, 1, 1, 0) ::
          Result("86602.006", "Сталь 20Х/120/56", 276, 1380, 5, 5, 1, 1, 0) ::
          Result("86401.905", "Сталь 20Х/120/56", 564, 1128, 2, 2, 1, 1, 0) ::
          Result("86602.038", "Сталь 20Х/120/56", 270, 1080, 4, 4, 1, 1, 0) :: Nil

      val combinations = cuttingStockProblem(in, lenghtThreshold, minLenght, logger)
      val expectedLength = in./:(0)((acc, c) ⇒ acc + c.length * c.cQuantity)
      val sumLen = combinations.map(_.sheets./:(0)((acc, c) ⇒ acc + c.lenght * c.quantity)).sum
      val actual = combinations.find(e ⇒ lenghtThreshold - e.rest < minLenght)
      val balance = combinations.map(_.rest).sum

      logger.debug(s"Sum-Lenght $sumLen")
      logger.debug(s"Combinations $combinations")

      actual.isDefined === false
      (sumLen + balance) === combinations.size * lenghtThreshold
    }

    "scenario1" in {
      val in =
        Result("93901.00.05.101", "Сталь 20Х2Н4А/115/45", 514, 1028, 2, 2, 1, 1, 0) ::
          Result("93901.05.003", "Сталь 20Х2Н4А/115/45", 503, 1006, 2, 2, 1, 1, 0) ::
          Result("93901.00.05.201", "Сталь 20Х2Н4А/115/45", 514, 1028, 2, 2, 1, 1, 0) ::
          Result("93901.00.05.301", "Сталь 20Х2Н4А/115/45", 514, 1028, 2, 2, 1, 1, 0) :: Nil

      val combinations = cuttingStockProblem(in, lenghtThreshold, minLenght, logger)
      val expectedLength = in./:(0)((acc, c) ⇒ acc + c.length * c.cQuantity)
      val sumLen = combinations.map(_.sheets./:(0)((acc, c) ⇒ acc + c.lenght * c.quantity)).sum
      val actual = combinations.find(e ⇒ lenghtThreshold - e.rest < minLenght)
      val balance = combinations.map(_.rest).sum

      logger.debug(s"Sum-Lenght $sumLen")
      logger.debug(s"Combinations $combinations")

      expectedLength === sumLen
      actual.isDefined === false
      (sumLen + balance) === combinations.size * lenghtThreshold
    }

    "scenario2" in {
      val in =
        List(Result("86501.420.009.111", "Сталь 38ХГМ/100/45", 120, 604, 5, 4, 1, 1, 0),
          Result("86501.420.001.900", "Сталь 38ХГМ/100/45", 200, 604, 3, 6, 1, 1, 0),
          Result("86501.420.001.900", "Сталь 38ХГМ/100/45", 200, 1200, 6, 6, 1, 1, 0),
          Result("94100.001.007.072", "Сталь 38ХГМ/100/45", 170, 684, 4, 3, 1, 1, 0),
          Result("94100.001.007.072", "Сталь 38ХГМ/100/45", 170, 684, 4, 3, 1, 1, 0))

      val combinations = cuttingStockProblem(in, lenghtThreshold, minLenght, logger)
      val expectedLength = in./:(0)((acc, c) ⇒ acc + c.length * c.cQuantity)
      val sumLen = combinations.map(_.sheets./:(0)((acc, c) ⇒ acc + c.lenght * c.quantity)).sum
      val actual = combinations.find(e ⇒ lenghtThreshold - e.rest < minLenght)
      val balance = combinations.map(_.rest).sum

      logger.debug(s"Sum-Lenght $sumLen")
      logger.debug(s"Combinations $combinations")

      expectedLength === sumLen
      actual.isDefined === false
      (sumLen + balance) === combinations.size * lenghtThreshold
    }

    "scenario3" in {
      val in = Result("94900.04.701", "Сталь 38ХГМ/260/78", 437, 874, 2, 2, 1, 1, 0) ::
        Result("94900.04.751", "Сталь 38ХГМ/260/78", 437, 874, 2, 2, 1, 1, 0) :: Nil

      val combinations = cuttingStockProblem(in, lenghtThreshold, minLenght, logger)
      val expectedLength = in./:(0)((acc, c) ⇒ acc + c.length * c.cQuantity)
      val sumLen = combinations.map(_.sheets./:(0)((acc, c) ⇒ acc + c.lenght * c.quantity)).sum
      val actual = combinations.find(e ⇒ lenghtThreshold - e.rest < minLenght)
      val balance = combinations.map(_.rest).sum

      logger.debug(s"Sum-Lenght $sumLen")
      logger.debug(s"Combinations $combinations")

      expectedLength === sumLen
      actual.isDefined === false
      (sumLen + balance) === combinations.size * lenghtThreshold
    }

    "scenario4" in {
      val in =
        List(Result("94900.04.701", "Сталь 38ХГМ/260/78", 437, 1315, 3, 2, 1, 1, 0),
          Result("94900.04.751", "Сталь 38ХГМ/260/78", 437, 874, 2, 2, 1, 1, 0),
          Result("94900.04.751", "Сталь 38ХГМ/260/78", 437, 874, 2, 2, 1, 1, 0))

      val combinations = cuttingStockProblem(in, lenghtThreshold, minLenght, logger)
      val expectedLength = in./:(0)((acc, c) ⇒ acc + c.length * c.cQuantity)
      val sumLen = combinations.map(_.sheets./:(0)((acc, c) ⇒ acc + c.lenght * c.quantity)).sum
      val actual = combinations.find(e ⇒ lenghtThreshold - e.rest < minLenght)
      val balance = combinations.map(_.rest).sum

      logger.debug(s"Sum-Lenght $sumLen")
      logger.debug(s"Combinations $combinations")

      expectedLength === sumLen
      actual.isDefined === false
      (sumLen + balance) === combinations.size * lenghtThreshold
    }

    "scenario5" in {
      val in =
        List(Result("94900.04.701", "Сталь 38ХГМ/260/78", 437, 874, 2, 2, 1, 1, 0),
          Result("94900.04.751", "Сталь 38ХГМ/260/78", 437, 874, 2, 2, 1, 1, 0),
          Result("94900.04.881", "Сталь 38ХГМ/260/78", 502, 1004, 2, 2, 1, 1, 0))

      val combinations = cuttingStockProblem(in, lenghtThreshold, minLenght, logger)
      val expectedLength = in./:(0)((acc, c) ⇒ acc + c.length * c.cQuantity)
      val sumLen = combinations.map(_.sheets./:(0)((acc, c) ⇒ acc + c.lenght * c.quantity)).sum
      val actual = combinations.find(e ⇒ lenghtThreshold - e.rest < minLenght)
      val balance = combinations.map(_.rest).sum

      logger.debug(s"Sum-Lenght $sumLen")
      logger.debug(s"Combinations $combinations")

      expectedLength === sumLen
      actual.isDefined === false
      (sumLen + balance) === combinations.size * lenghtThreshold
    }

    "scenario6" in {
      val in =
        List(Result("93900.00.01.177", "Сталь 38ХГМ/120/32", 416, 832, 2, 2, 1, 1, 0),
          Result("93920.00.00.022", "Сталь 38ХГМ/120/32", 504, 1008, 2, 2, 1, 1, 0), Result("86320.031", "Сталь 38ХГМ/120/32", 655, 1310, 2, 2, 1, 1, 0),
          Result("93900.00.01.178", "Сталь 38ХГМ/120/32", 429, 858, 2, 2, 1, 1, 0), Result("93930.005", "Сталь 38ХГМ/120/32", 384, 1152, 3, 3, 1, 1, 0),
          Result("93900.00.00.002", "Сталь 38ХГМ/120/32", 385, 1155, 3, 3, 1, 1, 0), Result("93920.01.00.022", "Сталь 38ХГМ/120/32", 504, 1008, 2, 2, 1, 1, 0),
          Result("93900.01.00.002", "Сталь 38ХГМ/120/32", 385, 1155, 3, 3, 1, 1, 0), Result("93900.00.01.001", "Сталь 38ХГМ/120/32", 335, 1005, 3, 3, 1, 1, 0),
          Result("93920.01.00.044", "Сталь 38ХГМ/120/32", 504, 1008, 2, 2, 1, 1, 0), Result("94100.00.00.043М2", "Сталь 38ХГМ/120/32", 290, 1160, 4, 4, 1, 1, 0),
          Result("94100.00.00.043-01М2", "Сталь 38ХГМ/120/32", 290, 1160, 4, 4, 1, 1, 0), Result("93920.01.04.022", "Сталь 38ХГМ/120/32", 544, 1088, 2, 2, 1, 1, 0),
          Result("93900.01.00.155", "Сталь 38ХГМ/120/32", 371, 1113, 3, 3, 1, 1, 0), Result("93900.00.07.159", "Сталь 38ХГМ/120/32", 464, 928, 2, 2, 1, 1, 0),
          Result("93900.00.03.002", "Сталь 38ХГМ/120/32", 464, 928, 2, 2, 1, 1, 0), Result("93900.00.07.026", "Сталь 38ХГМ/120/32", 464, 928, 2, 2, 1, 1, 0),
          Result("93900.00.04.014", "Сталь 38ХГМ/120/32", 403, 806, 2, 2, 1, 1, 0), Result("93900.00.06.002", "Сталь 38ХГМ/120/32", 403, 806, 2, 2, 1, 1, 0),
          Result("93900.00.04.018", "Сталь 38ХГМ/120/32", 390, 780, 2, 2, 1, 1, 0), Result("93930.03.005", "Сталь 38ХГМ/120/32", 403, 806, 2, 2, 1, 1, 0),
          Result("93900.00.06.026", "Сталь 38ХГМ/120/32", 390, 780, 2, 2, 1, 1, 0), Result("93900.00.04.015", "Сталь 38ХГМ/120/32", 403, 806, 2, 2, 1, 1, 0),
          Result("93930.03.015", "Сталь 38ХГМ/120/32", 403, 806, 2, 2, 1, 1, 0), Result("93900.00.06.005", "Сталь 38ХГМ/120/32", 403, 806, 2, 2, 1, 1, 0),
          Result("93930.04.016", "Сталь 38ХГМ/120/32", 403, 806, 2, 2, 1, 1, 0))

      val combinations = cuttingStockProblem(in, lenghtThreshold, minLenght, logger)
      val expectedLength = in./:(0)((acc, c) ⇒ acc + c.length * c.cQuantity)
      val sumLen = combinations.map(_.sheets./:(0)((acc, c) ⇒ acc + c.lenght * c.quantity)).sum
      val actual = combinations.find(e ⇒ lenghtThreshold - e.rest < minLenght)
      val balance = combinations.map(_.rest).sum

      logger.debug(s"Sum-Lenght $sumLen")
      logger.debug(s"Combinations $combinations")

      expectedLength === sumLen
      actual.isDefined === false
      (sumLen + balance) === combinations.size * lenghtThreshold
    }

    "scenario7" in {
      lenghtThreshold = 450 * 4

      val in =
        List(Result("XY", "Сталь 38ХГМ-260-78", 382, 382, 1, 1, 1, 1, 0),
          Result("DT", "Сталь 38ХГМ-260-78", 417, 1251, 3, 3, 1, 1, 0), Result("EX", "Сталь 38ХГМ-260-78", 371, 371, 1, 1, 1, 1, 0),
          Result("EF", "Сталь 38ХГМ-260-78", 432, 1728, 4, 4, 1, 1, 0), Result("RD", "Сталь 38ХГМ-260-78", 368, 1104, 3, 3, 1, 1, 0),
          Result("DV", "Сталь 38ХГМ-260-78", 399, 1596, 4, 4, 1, 1, 0), Result("TU", "Сталь 38ХГМ-260-78", 404, 404, 1, 1, 1, 1, 0))

      val combinations = cuttingStockProblem(in, lenghtThreshold, 400, logger)
      val expectedLength = in./:(0)((acc, c) ⇒ acc + c.length * c.cQuantity)
      val sumLen = combinations.map(_.sheets./:(0)((acc, c) ⇒ acc + c.lenght * c.quantity)).sum
      val actual = combinations.find(e ⇒ lenghtThreshold - e.rest < minLenght)
      val balance = combinations.map(_.rest).sum

      logger.debug(s"Sum-Lenght $sumLen")
      logger.debug(s"Combinations $combinations")

      expectedLength === sumLen
      actual.isDefined === false
      (sumLen + balance) === combinations.size * lenghtThreshold
    }

    "scenario8" in {
      lenghtThreshold = 450 * 4

      val in = List(Result("FG", "Сталь 38ХГМ-260-78,394", 394, 1, 1, 1, 1, 0), Result("FU", "Сталь 38ХГМ-260-78", 448, 1792, 4, 4, 1, 1, 0),
        Result("SJ", "Сталь 38ХГМ-260-78", 393, 1179, 3, 3, 1, 1, 0), Result("WY", "Сталь 38ХГМ-260-78", 388, 776, 2, 2, 1, 1, 0),
        Result("YS", "Сталь 38ХГМ-260-78", 357, 1428, 4, 4, 1, 1, 0), Result("RO", "Сталь 38ХГМ-260-78", 446, 892, 2, 2, 1, 1, 0),
        Result("RT", "Сталь 38ХГМ-260-78", 401, 401, 1, 1, 1, 1, 0))

      val combinations = cuttingStockProblem(in, lenghtThreshold, 400, logger)
      val expectedLength = in./:(0)((acc, c) ⇒ acc + c.length * c.cQuantity)
      val sumLen = combinations.map(_.sheets.foldLeft(0)((acc, c) ⇒ acc + c.lenght * c.quantity)).sum
      val actual = combinations.find(e ⇒ lenghtThreshold - e.rest < minLenght)
      val balance = combinations.map(_.rest).sum

      logger.debug(s"Sum-Lenght $sumLen")
      logger.debug(s"Combinations $combinations")

      expectedLength === sumLen
      actual.isDefined === false
      (sumLen + balance) === combinations.size * lenghtThreshold
    }

    "scenario9" in {
      lenghtThreshold = 450 * 4

      val in = List(Result("OV", "Сталь 38ХГМ-260-78", 389, 1556, 4, 4, 1, 1, 0), Result("RJ", "Сталь 38ХГМ-260-78", 358, 358, 1, 1, 1, 1, 0),
        Result("IR", "Сталь 38ХГМ-260-78", 400, 800, 2, 2, 1, 1, 0), Result("EH", "Сталь 38ХГМ-260-78", 353, 1059, 3, 3, 1, 1, 0),
        Result("BA", "Сталь 38ХГМ-260-78", 423, 846, 2, 2, 1, 1, 0), Result("UU", "Сталь 38ХГМ-260-78", 403, 1209, 3, 3, 1, 1, 0),
        Result("IA", "Сталь 38ХГМ-260-78", 418, 418, 1, 1, 1, 1, 0))

      val expectedLength = in./:(0)((acc, c) ⇒ acc + c.length * c.cQuantity)
      val combinations = cuttingStockProblem(in, lenghtThreshold, 400, logger)
      val sumLen = combinations.map(_.sheets./:(0)((acc, c) ⇒ acc + c.lenght * c.quantity)).sum
      val actual = combinations.find(e ⇒ lenghtThreshold - e.rest < minLenght)
      val balance = combinations.map(_.rest).sum

      logger.debug(s"Sum-Lenght $sumLen")
      logger.debug(s"Combinations $combinations")

      expectedLength === sumLen
      actual.isDefined === false
      (sumLen + balance) === combinations.size * lenghtThreshold
    }

    "scenario10" in {
      lenghtThreshold = 450 * 4

      val in = List(Result("WD", "Сталь 38ХГМ-260-78", 373, 373, 1, 1, 1, 1, 0), Result("TW", "Сталь 38ХГМ-260-78", 417, 834, 2, 2, 1, 1, 0),
        Result("EC", "Сталь 38ХГМ-260-78", 391, 391, 1, 1, 1, 1, 0), Result("VP", "Сталь 38ХГМ-260-78", 436, 1744, 4, 4, 1, 1, 0),
        Result("OU", "Сталь 38ХГМ-260-78", 394, 1576, 4, 4, 1, 1, 0), Result("YD", "Сталь 38ХГМ-260-78", 394, 1182, 3, 3, 1, 1, 0),
        Result("YO", "Сталь 38ХГМ-260-78", 393, 1572, 4, 4, 1, 1, 0))

      val expectedLength = in./:(0)((acc, c) ⇒ acc + c.length * c.cQuantity)
      val combinations = cuttingStockProblem(in, lenghtThreshold, 400, logger)
      val sumLen = combinations.map(_.sheets./:(0)((acc, c) ⇒ acc + c.lenght * c.quantity)).sum
      val actual = combinations.find(e ⇒ lenghtThreshold - e.rest < minLenght)
      val balance = combinations.map(_.rest).sum

      logger.debug(s"Sum-Lenght $sumLen")
      logger.debug(s"Combinations $combinations")

      expectedLength === sumLen
      actual.isDefined === false
      (sumLen + balance) === combinations.size * lenghtThreshold
    }

    "scenario-11-long-playing" in {
      lenghtThreshold = 450 * 4

      val in = List(Result("NO", "Сталь 38ХГМ-260-78", 449, 1347, 3, 3, 1, 1, 0),
        Result("RT", "Сталь 38ХГМ-260-78", 385, 1540, 4, 4, 1, 1, 0), Result("KL", "Сталь 38ХГМ-260-78", 445, 445, 1, 1, 1, 1, 0),
        Result("CA", "Сталь 38ХГМ-260-78", 429, 1716, 4, 4, 1, 1, 0), Result("ZP", "Сталь 38ХГМ-260-78", 431, 431, 1, 1, 1, 1, 0),
        Result("FN", "Сталь 38ХГМ-260-78", 376, 376, 1, 1, 1, 1, 0), Result("HZ", "Сталь 38ХГМ-260-78", 433, 1732, 4, 4, 1, 1, 0),
        Result("DX", "Сталь 38ХГМ-260-78", 416, 1664, 4, 4, 1, 1, 0), Result("YC", "Сталь 38ХГМ-260-78", 368, 736, 2, 2, 1, 1, 0),
        Result("MS", "Сталь 38ХГМ-260-78", 430, 860, 2, 2, 1, 1, 0), Result("FD", "Сталь 38ХГМ-260-78", 384, 1536, 4, 4, 1, 1, 0),
        Result("AN", "Сталь 38ХГМ-260-78", 415, 1660, 4, 4, 1, 1, 0), Result("LJ", "Сталь 38ХГМ-260-78", 445, 445, 1, 1, 1, 1, 0),
        Result("QV", "Сталь 38ХГМ-260-78", 422, 1266, 3, 3, 1, 1, 0), Result("NH", "Сталь 38ХГМ-260-78", 428, 428, 1, 1, 1, 1, 0),
        Result("SO", "Сталь 38ХГМ-260-78", 411, 822, 2, 2, 1, 1, 0), Result("LC", "Сталь 38ХГМ-260-78", 387, 1548, 4, 4, 1, 1, 0),
        Result("WV", "Сталь 38ХГМ-260-78", 421, 1684, 4, 4, 1, 1, 0), Result("BF", "Сталь 38ХГМ-260-78", 375, 1500, 4, 4, 1, 1, 0),
        Result("HN", "Сталь 38ХГМ-260-78", 431, 431, 1, 1, 1, 1, 0))

      val expectedLength = in./:(0)((acc, c) ⇒ acc + c.length * c.cQuantity)
      val combinations = cuttingStockProblem(in, lenghtThreshold, 400, logger)
      val sumLen = combinations.map(_.sheets./:(0)((acc, c) ⇒ acc + c.lenght * c.quantity)).sum
      val actual = combinations.find(e ⇒ lenghtThreshold - e.rest < minLenght)
      val balance = combinations.map(_.rest).sum

      logger.debug(s"Sum-Lenght $sumLen")
      logger.debug(s"Combinations $combinations")

      expectedLength === sumLen
      actual.isDefined === false
      (sumLen + balance) === combinations.size * lenghtThreshold
    }

    "scenario12" in {

      val in = List(Result("86501.420.009.111", "Сталь 38ХГМ/100/45", 120, 480, 4, 4, 1, 1, 0),
        Result("86501.420.001.900", "Сталь 38ХГМ/100/45", 200, 1200, 6, 6, 1, 1, 0), Result("86501.420.001.903", "Сталь 38ХГМ/100/45", 200, 404, 2, 6, 1, 1, 0),
        Result("94100.001.007.072", "Сталь 38ХГМ/100/45", 170, 510, 3, 3, 1, 1, 0), Result("94100.001.007.073", "Сталь 38ХГМ/100/45", 170, 510, 3, 3, 1, 1, 0),
        Result("86501.420.009.113", "Сталь 38ХГМ/100/45", 120, 724, 6, 4, 1, 1, 0))

      val expectedLength = in./:(0)((acc, c) ⇒ acc + c.length * c.cQuantity)

      val combinations = cuttingStockProblem(in, lenghtThreshold, 400, logger)
      val sumLen = combinations.map(_.sheets./:(0)((acc, c) ⇒ acc + c.lenght * c.quantity)).sum
      val actual = combinations.find(e ⇒ lenghtThreshold - e.rest < minLenght)
      val balance = combinations.map(_.rest).sum

      logger.debug(s"Sum-Lenght $sumLen")
      logger.debug(s"Combinations $combinations")

      expectedLength === sumLen
      actual.isDefined === false
      (sumLen + balance) === combinations.size * lenghtThreshold
    }

    "scenario13" in {
      val P = scalaz.stream.Process

      @tailrec def loop(lines: Iterator[String], acc: List[Int]): (String, List[Int]) = {
        if (lines.hasNext) {
          val cur = lines.next()
          val fields = cur.split(";")
          if (fields.length == 3) loop(lines, acc ::: List(fields(0).toInt, fields(1).toInt, fields(2).toInt))
          else (cur, acc)
        } else ("", acc)
      }

      val parse: (String) => (String, List[Int]) =
        in => {
          val elements = in.split("\\n").iterator
          loop(elements, Nil)
        }

      val src: scalaz.stream.Process[Task, String] = P.emitAll(Seq("1;2;3\n4;5;6\n7;8", "9;1\n1;2;3\n3;4;5\n6;7;89"))
      val flow = src pipe OrderService.stateScan[String,String,List[Int]]("") { batch: String =>
        for {
          acc <- State.get[String]
          r = parse(acc + batch)
          _ <- State.put(r._1)
        } yield r._2
      }

      flow.runLog.run === IndexedSeq(List(1, 2, 3, 4, 5, 6), List(7, 89, 1, 1, 2, 3, 3, 4, 5, 6, 7, 89))
    }
  }
}