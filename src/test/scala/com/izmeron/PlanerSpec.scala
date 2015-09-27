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

import org.specs2.mutable.Specification
import com.twitter.algebird.{Monoid ⇒ TwitterMonoid, Hash128, MinHashSignature, MinHasher, MinHasher32}

import scala.annotation.tailrec

class PlanerSpec extends Specification {
  val lenghtThreshold = 1200
  val minLenght = 400
  val log = org.apache.log4j.Logger.getLogger("test-planner")

  case class FollowersGraph[From, To](branches: Set[(From, To)]) {
    def propagate[T: TwitterMonoid](mapping: Map[From, T]): Map[To, T] =
      branches
        .groupBy(_._2)
        .mapValues { edges ⇒
          val vs = edges.map(fromTo ⇒ mapping.getOrElse(fromTo._1, TwitterMonoid.zero[T]))
          TwitterMonoid.sum(vs)
        }
  }

  def exactSimilarity[T](x: Set[T], y: Set[T]) =
    (x & y).size.toDouble / (x ++ y).size

  def approxSimilarity[T, H](mh: MinHasher[H], x: Set[T], y: Set[T]) = {
    val sigX = x.map(elem ⇒ mh.init(elem.toString)).reduce(mh.plus(_, _))
    val sigY = y.map(elem ⇒ mh.init(elem.toString)).reduce(mh.plus(_, _))
    mh.similarity(sigX, sigY)
  }
/*
  "Levels of followers" should {
    "run" in {

      val x = "qwerty".toSet
      val y = "qwerty1".toSet

      exactSimilarity(x,y)

      val hasher = new com.twitter.algebird.MinHasher32(1.0, 1024)
      val sig0 = x.map(elem => hasher.init(elem.toString)).reduce(hasher.plus(_, _))
      val sig1 = y.map(elem => hasher.init(elem.toString)).reduce(hasher.plus(_, _))

      hasher.buckets(sig0).zipWithIndex.map { case (bucket, ind) =>
        ((bucket, ind), Set(sig0))
      }

      hasher.buckets(sig1)
      hasher.similarity(sig0, sig1)
      approxSimilarity(hasher, x, y)

      val graph = FollowersGraph(Set(('E, 'B), ('F,'C), ('F,'D), ('G, 'D), ('G, 'F), ('B,'A), ('C,'A), ('D,'A)))
      val users = List('A,'B,'C,'D,'E,'F,'G).map(name => (name, Set(name))).toMap

      val firstFollowers = (graph propagate users)
      val secondFollowers = (graph propagate firstFollowers)
      val thirdFollowers = (graph propagate secondFollowers)

      println(s"1 level $firstFollowers")
      println(s"2 level $secondFollowers")
      println(s"3 level $thirdFollowers")


      import com.twitter.algebird.Aggregator.{ max, min, approximatePercentile, uniqueCount, approximateUniqueCount }
      (max[Int] join min[Int] join approximatePercentile[Int](0.9, 10))(Seq.range(1,100))

      (uniqueCount[Int] join approximateUniqueCount[Int])(Seq.range(1,100))

      1 === 1
    }
  }*/

  /*
  import cascading.flow.FlowDef
  import com.twitter.scalding._
  import com.twitter.scalding.typed._
  import com.twitter.scalding.IterableSource
  class WordCountJob(args: Args) extends Job(args) {
    com.twitter.scalding.TypedPipe.from(TextLine(args("input")))
      .flatMap { line => tokenize(line) }
      .groupBy { word => word } // use each word for a key
      .size // in each group, get the size
      .values
      .write(TypedSink(IterableSource[String](new Iterable[String]() {
        override def iterator: Iterator[String] = scala.io.Source.fromFile("./cvs/metal2pipes2.csv").getLines()
      })))

    // Split a piece of text into individual words.
    def tokenize(text: String): Array[String] = {
      // Lowercase each word and remove punctuation.
      text.toLowerCase.replaceAll("[^a-zA-Z0-9\\s]", "").split("\\s+")
    }
  }*/

  /*
  "scalding" should {
    "run" in {
      import com.twitter.scalding._
      import com.twitter.scalding.typed._
      import com.twitter.scalding.IterableSource

      implicit val F = FlowDef.flowDef()

      val latch = new CountDownLatch(1)
      val job = com.twitter.scalding.TypedPipe.from(TextLine("./cvs/metal2pipes2.csv"))
        .flatMap(_.split("\\;+"))
        .map(w ⇒ (w, 1l))
        .group
        .size
        .values
        .write(TypedSink(IterableSource[String](new Iterable[String]() {
          override def iterator: Iterator[String] = scala.io.Source.fromFile("./cvs/out.txt").getLines()
        })))

      job.onComplete { () ⇒ latch.countDown() }
      latch.await

      1 === 1
    }
  }*/
  /*
  "scalding" should {
    "run0" in {
      import com.twitter.scalding._
      import com.twitter.scalding.typed._
      import com.twitter.scalding.IterableSource
      import com.twitter.scalding.typed.TDsl._

      //com.twitter.scalding.typed.TypedPipe.from(
      com.twitter.scalding.TypedCsv[(String, Long)]("./cvs/metal2pipes2.csv")
        .map { case (itemId, userId) ⇒ (itemId, hasher.init(userId)) }
        .group[String, MinHashSignature]
        .sum[MinHashSignature](hasher)

      val hasher = new com.twitter.algebird.MinHasher32(1.0, 1024)
      //implicit val F = FlowDef.flowDef()
      TypedCsv[(String, Long)]("./cvs/metal2pipes2.csv")
        .map { case (itemId, userId) ⇒ (itemId, hasher.init(userId)) }
        .group[String, MinHashSignature]
        .sum[MinHashSignature]
        .flatMap {
          case (itemId, signature) ⇒
            hasher.buckets(signature).zipWithIndex.map {
              case (bucket, ind) ⇒
                ((bucket, ind), Set((itemId, signature)))
            }
        }
        .group[(Long, Int), Set[(String, MinHashSignature)]]
        .sum

      1 === 1
    }
  }*/

  "CuttingStockProblem" should {
    "run" in {
      var i = 0
      val maxSize = 1200 //200 * 9 + 170 * 8 + 120 * 5 = 3760

      //val blocks = Array[Int](700, 500, 250, 380)
      //val quantities = Array[Int](4, 3, 6, 5)

      //val x = 200 * 9 + 170 * 8 + 120 * 5

      //val blocks = Array[Int](200, 170, 120)
      //val quantities = Array[Int](9, 8, 5)
      //200 * 2
      //120 * 1
      //170 * 4

      //val blocks = Array[Int](200, 170, 120)
      //val quantities = Array[Int](7, 4, 4)
      //200 * 2
      //120 * 1
      //170 * 4

      //val blocks = Array[Int](200, 120)
      //val quantities = Array[Int](5, 3)
      //200 * 4
      //120 * 3 - 1160

      //val blocks = Array[Int](200, 120)
      //val quantities = Array[Int](1, 0)
      //*********************************************************

      //Exception
      //val blocks = Array[Int](220, 400, 340, 480, 510, 120)
      //val quantities = Array[Int](4, 4, 6, 5, 2, 6)

      //200 -> 1, 400 -> 4, 340 -> 1, 480 -> 1, 510 -> 2, 120 -> 1

      //val blocks = Array[Int](200, 400, 340, 480, 510, 120)
      //val quantities = Array[Int](1, 4, 1, 1, 2, 1)
      //prefer longest
      //480 * 1
      //400 * 1
      //200 * 1
      //120 * 1
      // Sum:1200

      //val blocks = Array[Int](400, 340, 510)
      //val quantities = Array[Int](3, 1, 2)
      //400 * 3
      // Sum:1200

      //val blocks = Array[Int](340, 510)
      //val quantities = Array[Int](1, 2)

      //340 * 1
      //510 * 1
      // Sum: 350

     //val blocks = Array[Int](510)
     //val quantities = Array[Int](1)

      //510 * 1
      // Sum: 690

      val blocks = Array[Int](200, 400, 340, 480, 510, 120)
      val quantities = Array[Int](1, 4, 1, 1, 2, 1)
      //120 * 1 / 200 * 1 / 400 * 1 / 480 *

      val r = collect(blocks, quantities, lenghtThreshold, log, Nil)
      println(r)


      //log.debug(com.izmeron.cut(blocks, quantities, maxSize, log))

      /*val list = Variant(List(Sheet(400,3)), 0) ::
          Variant(List(Sheet(120,1), Sheet(200,1), Sheet(400,1), Sheet(480,1)), 0) ::
          Variant(List(Sheet(120,1), Sheet(200,1)), 0) ::
          Variant(List(Sheet(510,2)), 180) ::
            Variant(List(Sheet(340,1)), 760) :: Nil


      val r = list./:(list.head) { (acc, c) =>
        if (acc == c) c
        else if (c.rest < acc.rest) c
        else if (c.list.size > acc.list.size) c
        else acc
      }
      println(r)*/

      /*var map: java.util.Map[Integer, Integer] = null
      val cuttingStock = new com.izmeron.CuttingStockProblem(maxSize, blocks, quantities)
      while (cuttingStock.hasMoreCombinations) {
        i += 1
        log.debug(s"Combination no $i")
        map = cuttingStock.nextBatch
        val iter = map.entrySet.iterator
        var sum = 0
        while (iter.hasNext) {
          val inner = iter.next
          sum += inner.getKey * inner.getValue
          log.debug(s"${inner.getKey} * ${inner.getValue}")
        }
        log.debug(s"Sum:$sum")
      }*/

      1 === 1
    }
  }

  /*
  "Binding to asynchronous sources" should {
    "run0" in {
      val map = collection.immutable.Map[String, List[Result]](
        "86501.420.001.900" -> (Result("86501.420.001.900", "Сталь 38ХГМ/100/45", length = 200, cLength = 604, cQuantity = 3, optQuantity = 6) ::
            Result("86501.420.001.900", "Сталь 38ХГМ/100/45", length = 200, cLength = 1200, cQuantity = 6, optQuantity = 6) :: Nil),
        "86501.420.009.111" -> (Result("86501.420.009.111", "Сталь 38ХГМ/100/45", length = 120, cLength = 480, cQuantity = 4, optQuantity = 4) :: Nil),
        "94100.001.007.072" -> (Result("94100.001.007.072", "Сталь 38ХГМ/100/45", length = 170, cLength = 684, cQuantity = 4, optQuantity = 3) ::
              Result("94100.001.007.072", "Сталь 38ХГМ/100/45", length = 170, cLength = 684, cQuantity = 4, optQuantity = 3) :: Nil)
      )

      val actual = distributeWithGroup(map, lenghtThreshold, minLenght, log)

      actual("86501.420.001.900") === List(Result("86501.420.001.900", "Сталь 38ХГМ/100/45", length=200, cLength=1200, cQuantity=6, optQuantity =6))
      actual("86501.420.001.900 - 94100.001.007.072") === List(
        Result("86501.420.001.900", "Сталь 38ХГМ/100/45", length = 200, cLength = 200, cQuantity = 1, optQuantity = 6),
        Result("94100.001.007.072", "Сталь 38ХГМ/100/45", length = 170, cLength = 684, cQuantity = 4, optQuantity = 3),
        Result("86501.420.001.900", "Сталь 38ХГМ/100/45", length = 200, cLength = 200, cQuantity = 1, optQuantity = 6),
        Result("94100.001.007.072", "Сталь 38ХГМ/100/45", length = 170, cLength = 684, cQuantity = 4, optQuantity = 3)
      )

      actual("86501.420.001.900 - 86501.420.009.111") === List(
        Result("86501.420.001.900", "Сталь 38ХГМ/100/45", length = 200, cLength = 200, cQuantity = 1, optQuantity = 6),
        Result("86501.420.009.111", "Сталь 38ХГМ/100/45", length = 120, cLength = 480, cQuantity = 4, optQuantity = 4))
    }
  }


  "Binding to asynchronous sources" should {
    "run1" in {
      val map = collection.immutable.Map[String, List[Result]](
        "86501.420.001.900" -> (Result("86501.420.001.900", "Сталь 38ХГМ/100/45", length = 200, cLength = 404, cQuantity = 2, optQuantity = 6) ::
          Result("86501.420.001.900", "Сталь 38ХГМ/100/45", length = 200, cLength = 1200, cQuantity = 6, optQuantity = 6) :: Nil),
        "86501.420.009.111" -> (Result("86501.420.009.111", "Сталь 38ХГМ/100/45", length = 120, cLength = 480, cQuantity = 4, optQuantity = 4) :: Nil),
        "94100.001.007.072" -> (Result("94100.001.007.072", "Сталь 38ХГМ/100/45", length = 170, cLength = 684, cQuantity = 4, optQuantity = 3) ::
          Result("94100.001.007.072", "Сталь 38ХГМ/100/45", length = 170, cLength = 684, cQuantity = 4, optQuantity = 3) :: Nil)
      )

      val actual = distributeWithGroup(map, lenghtThreshold, minLenght, log)
      1 === 1
    }
  }

  "Binding to asynchronous sources" should {
    "run2" in {
      val map = collection.immutable.Map[String, List[Result]](
        "86501.420.001.900" -> (Result("86501.420.001.900", "Сталь 38ХГМ/100/45", length = 200, cLength = 204, cQuantity = 1, optQuantity = 6) ::
          Result("86501.420.001.900", "Сталь 38ХГМ/100/45", length = 200, cLength = 1200, cQuantity = 6, optQuantity = 6) :: Nil),
        "86501.420.009.111" -> (Result("86501.420.009.111", "Сталь 38ХГМ/100/45", length = 120, cLength = 480, cQuantity = 4, optQuantity = 4) :: Nil),
        "94100.001.007.072" -> (Result("94100.001.007.072", "Сталь 38ХГМ/100/45", length = 170, cLength = 684, cQuantity = 4, optQuantity = 3) ::
          Result("94100.001.007.072", "Сталь 38ХГМ/100/45", length = 170, cLength = 684, cQuantity = 4, optQuantity = 3) :: Nil)
      )

      val actual = distributeWithGroup(map, lenghtThreshold, minLenght, log)
      1 === 1
    }
  }*/
}
