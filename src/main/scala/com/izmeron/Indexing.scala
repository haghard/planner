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

import java.io.FileInputStream
import akka.actor.ActorSystem
import akka.stream.io.Framing
import akka.stream.scaladsl.StreamConverters
import akka.util.ByteString
import akka.stream.{ ActorMaterializerSettings, ActorMaterializer, Supervision, ActorAttributes }
import com.typesafe.config.Config

import scala.collection.mutable
import scala.concurrent.Future

trait Indexing {

  def minLenght: Int
  def indexPath: String
  def lenghtThreshold: Int
  def httpPort: Int
  def cfg: Config

  implicit lazy val system: ActorSystem = ActorSystem("Sys", cfg)

  lazy val Settings = ActorMaterializerSettings(system)
    .withInputBuffer(initialSize = 128, maxSize = 256)
    .withDispatcher("akka.planner")

  implicit lazy val materializer: ActorMaterializer = ActorMaterializer(Settings)
  implicit lazy val dispatcher = materializer.executionContext

  def parseCsv(bs: ByteString): Etalon = {
    val items = bs.utf8String.split(';')
    Etalon(items(0), items(1), items(2), items(3),
      items(4).replaceAll(cvsSpace, empty).toInt,
      items(5).replaceAll(cvsSpace, empty).toInt,
      items(6).replaceAll(cvsSpace, empty).toInt,
      items(7).replaceAll(cvsSpace, empty).toInt,
      items(8).replaceAll(cvsSpace, empty).toInt,
      items(9).replaceAll(cvsSpace, empty).toInt,
      items(10).replaceAll(cvsSpace, empty).toInt,
      items(11).replaceAll(cvsSpace, empty).toInt,
      items(12).replaceAll(cvsSpace, empty).toInt)
  }

  private def parseLen(bs: ByteString): Int = {
    val items = bs.utf8String.split(';')
    items(5).replaceAll(cvsSpace, empty).toInt
  }

  case class View(kd: String, len: Int, lenMin: Int, numPart: Int)
  private def parseView(bs: ByteString): View = {
    val items = bs.utf8String.split(';')
    View(items(0), items(5).replaceAll(cvsSpace, empty).toInt,
      items(9).replaceAll(cvsSpace, empty).toInt,
      items(11).replaceAll(cvsSpace, empty).toInt)
  }

  val sep = ByteString(System.lineSeparator)

  def parseOrder(bs: ByteString): Order = {
    val items = bs.utf8String.split(';')
    Order(items(0), items(13).toInt)
  }

  def createFileIndex: Future[mutable.Map[String, RawResult]] = {
    (StreamConverters.fromInputStream(() ⇒ new FileInputStream(indexPath)) via Framing.delimiter(sep, Int.MaxValue, true))
      .map(parseCsv)
      .withAttributes(ActorAttributes.supervisionStrategy(_ ⇒ Supervision.Stop))
      .runFold(mutable.Map[String, RawResult]()) { (acc, c) ⇒
        val key = s"${c.marka}/${c.diam}/${c.indiam}"
        acc += (c.kd -> RawResult(c.kd, key, c.qOptimal, c.len, c.lenMin, c.tProfit, c.qMin, c.numSect))
        acc
      }
  }

  private def readOrders: Future[List[Order]] = {
    (StreamConverters.fromInputStream(() ⇒ new FileInputStream(indexPath)) via Framing.delimiter(sep, Int.MaxValue, true))
      .map(parseOrder)
      .withAttributes(ActorAttributes.supervisionStrategy(_ ⇒ Supervision.Stop))
      .runFold(List[Order]())((acc, c) ⇒ c :: acc)
  }

  def indexedOrders: Future[(List[Order], mutable.Map[String, RawResult])] = (readOrders zip createFileIndex)

  def maxLengthCheck: Future[Int] =
    (StreamConverters.fromInputStream(() ⇒ new FileInputStream(indexPath)) via Framing.delimiter(sep, Int.MaxValue, true))
      .map(parseLen)
      .withAttributes(ActorAttributes.supervisionStrategy(_ ⇒ Supervision.Stop))
      .runFold(0)((acc, c) ⇒ if (acc > c) acc else c)

  def multiplicity: Future[List[String]] =
    (StreamConverters.fromInputStream(() ⇒ new FileInputStream(indexPath)) via Framing.delimiter(sep, Int.MaxValue, true))
      .map(parseView)
      .withAttributes(ActorAttributes.supervisionStrategy(_ ⇒ Supervision.Stop))
      .runFold(List[String]()) { (acc, c) ⇒
        val counted = c.lenMin * c.numPart
        if (c.len != counted) s"[${c.kd}: Expected:$counted - Actual:${c.len}]" :: acc
        else acc
      }
}