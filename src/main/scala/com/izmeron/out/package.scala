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

import simulacrum.typeclass

package object out {
  import scalaz.effect.IO
  import java.io.{ PrintWriter, File }


  trait Module {
    type From
    type To
  }

  trait JsonModule extends Module {
    type From = spray.json.JsObject
    type To = String
  }

  trait ExcelModule extends Module {
    type From = Set[info.folone.scala.poi.Row]
    type To = info.folone.scala.poi.Workbook
  }

  @typeclass trait Emitter[T <: Module] {
    def empty: T#To
    def write(v: T#To, outputFile: String): IO[Unit]
    def convert(v: T#From): T#To
    def monoid: scalaz.Monoid[T#From]
    def monoidMapper: (Int, List[Combination]) ⇒ T#From
    def Zero: T#From
  }


  object Emitter {
    import spray.json.{ JsArray, JsNumber, JsString, _ }

    implicit def json = new Emitter[JsonModule] {
      private val enc = "UTF-8"

      override val Zero = JsObject("uri" -> JsString("/orders"), "body" -> JsArray())

      override val monoid = new scalaz.Monoid[JsonModule#From] {
        override def zero = Zero
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

      override def monoidMapper: (Int, List[Combination]) ⇒ JsonModule#From =
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

      override def empty: JsonModule#To = JsObject().prettyPrint
      override def convert(v: JsonModule#From) = v.prettyPrint

      override def write(v: JsonModule#To, outputFile: String): IO[Unit] = {
        val out = new PrintWriter(new File(outputFile), enc)
        IO { out.print(v.asInstanceOf[String]) }.ensuring(IO { out.close() })
      }
    }

    implicit def excel = new Emitter[ExcelModule] {
      import info.folone.scala.poi._
      import java.util.concurrent.atomic.AtomicInteger
      private val counter = new AtomicInteger(0)

      override val Zero: Set[Row] = Set.empty[Row]

      override val monoid = new scalaz.Monoid[ExcelModule#From] {
        override val zero = Zero
        override def append(f1: Set[Row], f2: ⇒ Set[Row]): Set[Row] = f1 ++ f2
      }

      override def empty: ExcelModule#To =
        Workbook(Set(Sheet("plan") { Set(Row(0) { Set(StringCell(1, "")) }) }))

      override def monoidMapper: (Int, List[Combination]) ⇒ ExcelModule#From =
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

      override def convert(v: ExcelModule#From) = Workbook(Set(info.folone.scala.poi.Sheet("plan")(v)))

      override def write(v: ExcelModule#To, outputFile: String): IO[Unit] = {
        v.safeToFile(outputFile).fold(ex ⇒ throw ex, identity)
      }
    }
  }
}