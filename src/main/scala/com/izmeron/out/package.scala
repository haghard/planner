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

package object out {

  import scalaz.effect.IO
  import java.io.{ PrintWriter, File }
  import scala.annotation.implicitNotFound

  trait OutputModule {
    type From
    type To
  }

  trait JsonOutputModule extends OutputModule {
    type From = spray.json.JsObject
    type To = String
  }

  trait ExcelOutputModule extends OutputModule {
    type From = Set[info.folone.scala.poi.Row]
    type To = info.folone.scala.poi.Workbook
  }

  @implicitNotFound(msg = "Cannot find OutputWriter type class for ${T}")
  trait OutputWriter[T <: OutputModule] {
    def empty: T#To
    def write(v: T#To, outputDir: String): IO[Unit]
    def convert(v: T#From): T#To
    def monoid: scalaz.Monoid[T#From]
    def monoidMapper: (Int, List[Combination]) ⇒ T#From
  }

  object OutputWriter {
    import spray.json.{ JsArray, JsNumber, JsString, _ }

    def apply[T <: OutputModule](implicit writer: OutputWriter[T]): OutputWriter[T] = writer

    implicit val json = new OutputWriter[JsonOutputModule] {
      private val enc = "UTF-8"

      override val monoid = new scalaz.Monoid[JsonOutputModule#From] {
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

      override def monoidMapper: (Int, List[Combination]) ⇒ JsonOutputModule#From =
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

      override def empty: JsonOutputModule#To = JsObject().prettyPrint
      override def convert(v: JsonOutputModule#From) = v.prettyPrint

      override def write(v: JsonOutputModule#To, outputDir: String): IO[Unit] = {
        val out = new PrintWriter(new File(s"$outputDir/plan_${System.currentTimeMillis()}.json"), enc)
        IO { out.print(v.asInstanceOf[String]) }.ensuring(IO { out.close() })
      }
    }

    import info.folone.scala.poi._
    implicit val excel = new OutputWriter[ExcelOutputModule] {
      import java.util.concurrent.atomic.AtomicInteger
      private val counter = new AtomicInteger(0)

      override val monoid = new scalaz.Monoid[ExcelOutputModule#From] {
        override def zero: Set[Row] = Set.empty[Row]
        override def append(f1: Set[Row], f2: ⇒ Set[Row]): Set[Row] = f1 ++ f2
      }

      override def empty: ExcelOutputModule#To =
        Workbook(Set(Sheet("plan") { Set(Row(0) { Set(StringCell(1, "")) }) }))

      override def monoidMapper: (Int, List[Combination]) ⇒ ExcelOutputModule#From =
        (threshold, combinations) ⇒ {
          val header = Set(Row(counter.incrementAndGet())(Set(StringCell(1, s"${combinations.head.groupKey} Макс длинна: $threshold"))))
          combinations./:(header) { (acc, c) ⇒
            val local = c.sheets./:(acc) { (acc0, c) ⇒
              acc0 + Row(counter.incrementAndGet) {
                Set(StringCell(1, c.kd), NumericCell(2, c.lenght), NumericCell(3, c.quantity))
              }
            }
            val bottom = Row(counter.incrementAndGet)(Set(StringCell(1, s"Остаток: ${c.rest}"), StringCell(2, s"Длинна: ${threshold - c.rest}")))
            val separator = Row(counter.incrementAndGet)(Set())
            local + bottom + separator
          }
        }

      override def convert(v: ExcelOutputModule#From) = Workbook(Set(info.folone.scala.poi.Sheet("plan")(v)))

      override def write(v: ExcelOutputModule#To, outputDir: String): IO[Unit] = {
        v.safeToFile(s"$outputDir/plan_${System.currentTimeMillis()}.xls")
          .fold(ex ⇒ throw ex, identity)
      }
    }
  }
}
