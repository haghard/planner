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

import scalaz.effect.IO
import java.io.{ PrintWriter, File }

object OutputWriter {
  import spray.json.{ JsArray, JsNumber, JsString, _ }

  def apply[T](implicit writer: OutputWriter[T]): OutputWriter[T] = writer

  implicit val json = new OutputWriter[JsObject] {
    private val enc = "UTF-8"

    override val monoid = new scalaz.Monoid[JsObject] {
      override def zero = JsObject("uri" -> JsString("/orders"), "body" -> JsArray())
      override def append(f1: JsObject, f2: ⇒ JsObject): JsObject = {
        (f1, f2) match {
          case (f1: JsObject, f2: JsValue) ⇒
            JsObject("uri" -> f1.fields("uri").asInstanceOf[JsString],
              "body" -> JsArray(f1.fields("body").asInstanceOf[JsArray].elements.:+(f2)))
        }
      }
    }

    private val writerSheet = new spray.json.JsonWriter[Sheet] {
      override def write(s: Sheet): JsValue =
        JsObject("kd" -> JsString(s.kd),
          "lenght" -> JsNumber(s.lenght), "quantity" -> JsNumber(s.quantity))
    }

    override def mapper: (Int, List[Combination]) ⇒ JsObject =
      (threshold, combinations) ⇒ {
        val init = JsObject("group" -> JsString(combinations.head.groupKey), "body" -> JsArray())
        combinations./:(init) { (acc, c) ⇒
          JsObject(
            "group" -> acc.fields("group"),
            "body" -> JsArray(acc.fields("body").asInstanceOf[JsArray].elements.:+(JsObject(
              "sheet" -> JsArray(c.sheets.toVector.map(writerSheet.write)),
              "balance" -> JsNumber(c.rest),
              "lenght" -> JsNumber(threshold - c.rest)
            )))
          )
        }
      }

    override def empty[A]: A = {
      //val clazz = implicitly[ClassTag[A]].runtimeClass
      JsObject().prettyPrint.asInstanceOf[A]
    }

    override def convert[A](v: JsObject): A = v.prettyPrint.asInstanceOf[A]

    override def write[A](v: A, outputDir: String): IO[Unit] = {
      val out = new PrintWriter(new File(s"$outputDir/plan_${System.currentTimeMillis()}.json"), enc)
      IO { out.print(v.asInstanceOf[String]) }.ensuring(IO { out.close() })
    }
  }

  import info.folone.scala.poi._
  implicit val excel = new OutputWriter[Set[Row]] {
    import java.util.concurrent.atomic.AtomicInteger
    private val counter = new AtomicInteger(0)

    override val monoid = new scalaz.Monoid[Set[info.folone.scala.poi.Row]] {
      override def zero: Set[Row] = Set.empty[Row]
      override def append(f1: Set[Row], f2: ⇒ Set[Row]): Set[Row] = f1 ++ f2
    }

    override def empty[A] =
      Workbook(Set(Sheet("empty") {
        Set(Row(0) {
          Set(StringCell(1, ""))
        })
      })).asInstanceOf[A]

    override def mapper: (Int, List[Combination]) ⇒ Set[Row] =
      (threshold, combinations) ⇒ {
        counter.incrementAndGet()
        val header = Set(Row(counter.get())(Set(StringCell(1, s"${combinations.head.groupKey} Макс длинна: $threshold"))))
        combinations./:(header) { (acc, c) ⇒
          val local = c.sheets./:(acc) { (acc0, c) ⇒
            counter.incrementAndGet
            acc0 + Row(counter.get) {
              Set(StringCell(1, c.kd), NumericCell(2, c.lenght), NumericCell(3, c.quantity))
            }
          }
          counter.incrementAndGet
          val bottom = Row(counter.get)(Set(StringCell(1, s"Остаток: ${c.rest}"), StringCell(2, s"Длинна: ${threshold - c.rest}")))
          counter.incrementAndGet
          val separator = Row(counter.get)(Set())
          local + bottom + separator
        }
      }

    override def convert[A](v: Set[Row]): A =
      Workbook(Set(info.folone.scala.poi.Sheet("plan")(v))).asInstanceOf[A]

    override def write[A](v: A, outputDir: String): IO[Unit] = {
      v.asInstanceOf[Workbook].safeToFile(s"$outputDir/plan_${System.currentTimeMillis()}.xls")
        .fold(ex ⇒ throw ex, identity)
    }
  }
}

trait OutputWriter[T] {
  def write[A](v: A, outputDir: String): IO[Unit]
  def empty[A]: A
  def convert[A](v: T): A
  def monoid: scalaz.Monoid[T]
  def mapper: (Int, List[Combination]) ⇒ T
}
