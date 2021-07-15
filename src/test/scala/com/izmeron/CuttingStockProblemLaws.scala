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

import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.scalacheck._
import Prop._

object CuttingStockProblemLaws extends Properties("CuttingStockProblemLaws") {
  var count = 0
  val Size = 13
  val maxSize = 450
  val maxNum = 5
  val minLenght = 400
  val threshold = maxSize * maxNum
  val groupKey = "Сталь 38ХГМ-260-78"

  val logger = org.mockito.Mockito.mock(classOf[akka.event.LoggingAdapter])
  org.mockito.Mockito.when(logger.debug(org.mockito.Matchers.anyString()))
    .then(new Answer[Unit] {
      override def answer(invocationOnMock: InvocationOnMock): Unit = {
        val args = invocationOnMock.getArguments
        println(args(0).toString)
      }
    })

  property("cutting-stock-problem") = forAll(
    Gen.containerOfN[List, Result](
      Size,
      for {
        k ← Gen.alphaUpperChar
        d ← Gen.alphaUpperChar
        size ← Gen.choose(1, maxNum)
        detail ← Gen.choose(350, maxSize)
      } yield Result(new String(Array(k, d)), groupKey, detail, detail * size, size, size, 1, 1, 0)
    )) { list: List[Result] ⇒
      if (list.size == Size && !list.exists(_.kd == "")) {
        count += 1

        val combinations = cuttingStockProblem(list, threshold, minLenght, logger)
        val expectedLength = list./:(0)((acc, c) ⇒ acc + c.length * c.cQuantity)

        val sumLen = combinations.map(_.sheets./:(0)((acc, c) ⇒ acc + c.lenght * c.quantity)).sum
        val actual = combinations.find(e ⇒ threshold - e.rest < minLenght)
        val balance = combinations.map(_.rest).sum

        (list.size == Size) :| "Batch size isn't obeyed" &&
          (expectedLength == sumLen) :| "Final sum doesn't match" &&
          actual.isEmpty :| "Impossible element has been found" &&
          ((sumLen + balance) == combinations.size * threshold) :| "Final size isn't corresponded"
      } else { true :| "Check nothing" }
    }
}