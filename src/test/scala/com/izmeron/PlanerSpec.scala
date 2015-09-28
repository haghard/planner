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
import scalaz.concurrent.Task

class PlanerSpec extends Specification {
  val lenghtThreshold = 1200
  val minLenght = 400
  val logger = org.apache.log4j.Logger.getLogger("test-planner")

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

  "Read file and analyze" should {
    "run" in {
      import scalaz._, Scalaz._
      import com.ambiata.origami._, Origami._
      import com.ambiata.origami.stream.FoldableProcessM._
      import com.ambiata.origami._, Origami._

      object TestPlanner extends Planner {
        override val log = logger
        override val minLenght = 400
        override val lenghtThreshold = 1200
        override val path = "./cvs/metal2pipes2.csv"

        override def start(): Unit = {
          //val (l,r) = (highs run csvProcess).run.run
          //log.debug(s" $l - $r")
          /*
          val res = (groupByKey run csvProcess).run.run.values.find { line =>
            line.count(_ == '-') > 1
          }
          res.fold(log.debug("0"))(line => log.debug(line))
          */
          val map = (groupByKeyMinLen run csvProcess).run.run
          log.debug(s"Max: ${map.values.maxBy(_.length)}")

          val sames = map.filter { kv =>
            val lens = kv._2.split("-")
            var ind = 0
            var found = false

            while(!found && ind < lens.size) {
              val current = lens(ind)
              ind += 1
              found = lens.filter(_ == current).size > 1
            }
            found
          }

          logger.debug(sames.size)
          logger.debug(sames)
        }

        override def shutdown(): Unit = ???
      }

      TestPlanner.start()
      1 === 1
    }
  }

  "cuttingStockProblem" should {
    val threshold = 1400
    val minLenght = 400
    "scenario0" in {
      val rs =
        Result("86401.095", "Сталь 20Х/120/56", 614, 1228, 2, 2, 1, 1, 0) ::
        Result("86602.056", "Сталь 20Х/120/56", 275, 825, 3, 3, 1, 1, 0) ::
        Result("86602.006", "Сталь 20Х/120/56", 276, 1380, 5, 5, 1, 1, 0) ::
        Result("86401.905", "Сталь 20Х/120/56", 564, 1128, 2, 2, 1, 1, 0) ::
        Result("86602.038", "Сталь 20Х/120/56", 270, 1080, 4, 4, 1, 1, 0) :: Nil

      val r = cuttingStockProblem(rs, threshold, minLenght, logger)
      logger.debug(r)

      val actual = r.find(e => threshold - e.rest <= minLenght)
      logger.debug(actual)
      actual.isDefined === false
    }
/*
    "scenario1" in {
      val rs =
        Result("93901.00.05.101","Сталь 20Х2Н4А/115/45",514,1028,2,2,1,1,0) ::
          Result("93901.05.003","Сталь 20Х2Н4А/115/45",503,1006,2,2,1,1,0) ::
          Result("93901.00.05.201","Сталь 20Х2Н4А/115/45",514,1028,2,2,1,1,0) ::
          Result("93901.00.05.301","Сталь 20Х2Н4А/115/45",514,1028,2,2,1,1,0) :: Nil

      val r = cuttingStockProblem(rs, threshold, minLenght, logger)
      logger.debug(r)

      val actual = r.find(e => threshold - e.rest <= minLenght)
      logger.debug(actual)
      actual.isDefined === false
    }

    "scenario3" in {
      val rs = List(Result("86501.420.009.111","Сталь 38ХГМ/100/45",120,604,5,4,1,1,0),
        Result("86501.420.001.900","Сталь 38ХГМ/100/45",200,604,3,6,1,1,0),
        Result("86501.420.001.900","Сталь 38ХГМ/100/45",200,1200,6,6,1,1,0),
        Result("94100.001.007.072","Сталь 38ХГМ/100/45",170,684,4,3,1,1,0),
        Result("94100.001.007.072","Сталь 38ХГМ/100/45",170,684,4,3,1,1,0))

      val r = cuttingStockProblem(rs, threshold, minLenght, logger)
      logger.debug(r)

      val actual = r.find(e => threshold - e.rest <= minLenght)
      logger.debug(actual)
      actual.isDefined === false
    }

    "scenario4" in {
      val rs = Result("94900.04.701","Сталь 38ХГМ/260/78",437,874,2,2,1,1,0) ::
          Result("94900.04.751","Сталь 38ХГМ/260/78",437,874,2,2,1,1,0) :: Nil

      val r = cuttingStockProblem(rs, threshold, minLenght, logger)
      logger.debug(r)

      val actual = r.find(e => threshold - e.rest <= minLenght)
      logger.debug(actual)
      actual.isDefined === false
    }

    "scenario4" in {
      val rs =
        List(Result("94900.04.701","Сталь 38ХГМ/260/78",437,1315,3,2,1,1,0),
          Result("94900.04.751","Сталь 38ХГМ/260/78",437,874,2,2,1,1,0),
          Result("94900.04.751","Сталь 38ХГМ/260/78",437,874,2,2,1,1,0))

      val r = cuttingStockProblem(rs, threshold, minLenght, logger)
      logger.debug(r)

      val actual = r.find(e => threshold - e.rest <= minLenght)
      logger.debug(actual)
      actual.isDefined === false
    }
*/

    "scenario5" in {
      val rs =
        List(Result("94900.04.701","Сталь 38ХГМ/260/78",437,874,2,2,1,1,0),
          Result("94900.04.751","Сталь 38ХГМ/260/78",437,874,2,2,1,1,0),
          Result("94900.04.881","Сталь 38ХГМ/260/78",502,1004,2,2,1,1,0))

      val r = cuttingStockProblem(rs, threshold, minLenght, logger)
      logger.debug(r)

      val actual = r.find(e => threshold - e.rest <= minLenght)
      logger.debug(actual)
      actual.isDefined === false
    }
  }
}