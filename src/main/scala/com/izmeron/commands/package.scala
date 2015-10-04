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

import com.izmeron.out.{OutputWriter, OutputModule}

import scalaz.concurrent.Task
import scalaz.{ -\/, \/- }

package object commands {

  sealed trait CliCommand {
    def start(): Unit
  }

  object Exit extends CliCommand {
    override def start() = System.exit(0)
  }

  object Empty extends CliCommand {
    override def start() = ()
  }

  object StaticCheck {
    val ok = "Ok"
    val separator = ";"
    val rule0 = "0. Max lenght rule violation"
    val rule1 = "1. Multiplicity violation"

    def apply(path: String, minLenght: Int, lenghtThreshold: Int): StaticCheck =
      new StaticCheck(path, minLenght, lenghtThreshold)
  }

  final class StaticCheck(override val indexPath: String, override val minLenght: Int,
                          override val lenghtThreshold: Int) extends CliCommand with OrigamiAggregator {
    import StaticCheck._
    override val log = org.apache.log4j.Logger.getLogger("static-check")

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
          if (list.isEmpty) println(s"${Ansi.blueMessage(rule1)}: ${Ansi.blueMessage(ok)}")
          else println(s"${Ansi.blueMessage(rule1)}: ${Ansi.green(list.size.toString)}: ${Ansi.errorMessage(list.mkString(separator))}")
        case ((-\/(ex)), None) ⇒ println(s"${Ansi.blueMessage(rule1)}: ${Ansi.errorMessage(ex.getMessage)}")
        case other             ⇒ println(s"${Ansi.blueMessage(rule1)}: ${Ansi.errorMessage(other.toString())}")
      })
    }
  }

  object Plan {
    def apply[T <: OutputModule](path: String, outputDir: String, outFormat: String, minLenght: Int, lenghtThreshold: Int)
                                (implicit writer: OutputWriter[T]) =
      new Plan[T](path, outputDir, outFormat, minLenght, lenghtThreshold, writer)
  }

  final class Plan[T <: OutputModule](override val indexPath: String, outputDir: String, outFormat: String, override val minLenght: Int,
                      override val lenghtThreshold: Int, writer: OutputWriter[T]) extends CliCommand with OrigamiAggregator
  with ScalazFlowSupport {
    import scalaz.stream.merge
    import scalaz.stream.async

    implicit val ex = PlannerEx
    implicit val CpuIntensive = scalaz.concurrent.Strategy.Executor(PlannerEx)

    override val log = org.apache.log4j.Logger.getLogger("planner")

    /**
     * Computation graph
     *
     * File            Parallel stage                                         Parallel stage
     * +----------+   +-----------+                                          +------------+
     * |csv_line0 |---|distribute |--+                                  +----|cuttingStock|----+
     * +----------+   +-----------+  |  Fan-in stage                    |    +------------+    |
     * +----------+   +-----------+  |  +----------+  +-------------+   |    +------------+    |  +------------+   +-------+
     * |csv_line1 |---|distribute |-----|foldMonoid|--|bounded queue|--------|cuttingStock|-------|monoidMapper|---|convert|
     * +----------+   +-----------+  |  +----------+  +-------------+   |    +------------+    |  +------------+   +-------+
     *                               |                                  |    +------------+    |
     * +----------+   +-----------+  |                                  +----|cuttingStock|----+
     * |csv_line2 |---|distribute |--+                                       +------------+
     * +----------+   +-----------+
     */
    override def start(): Unit = {
      createIndex.runAsync {
        case \/-((\/-(index), None)) ⇒ {
          println(Ansi.green("Index has been created"))
          log.debug("Index has been created")
          Task.fork {
            val queue = async.boundedQueue[List[Result]](parallelism * parallelism)
            (inputReader(orderReader map (distribute(_, index)), queue).drain merge merge.mergeN(parallelism)(cuttingStock(queue)))
              .foldMap(writer.monoidMapper(lenghtThreshold, _))(writer.monoid)
              .runLast
              .map(_.fold(writer.empty)(writer.convert))
          }.runAsync {
            case \/-(result) ⇒
              writer.write(result, outputDir).unsafePerformIO()
              println(Ansi.green(s"$result"))
            case -\/(error) ⇒
              println(Ansi.red(s"${error.getClass.getName}: ${error.getStackTrace.mkString("\n")}: ${error.getMessage}"))
              log.debug(s"${error.getClass.getName}: ${error.getStackTrace.mkString("\n")}: ${error.getMessage}")
          }
        }
        case -\/(ex) ⇒
          println(Ansi.red(s"${ex.getClass.getName}: ${ex.getStackTrace.mkString("\n")}: ${ex.getMessage}"))
          log.debug(s"${ex.getClass.getName}: ${ex.getStackTrace.mkString("\n")}: ${ex.getMessage}")
        case \/-((-\/(ex), None)) ⇒
          println(Ansi.red(s"Error while building index: ${ex.getMessage}"))
          log.debug(s"Error while building index: ${ex.getMessage}")
        case \/-((_, Some(ex))) ⇒
          println(Ansi.red(s"Finalizer error while building index: ${ex.getMessage}"))
          log.debug(s"Finalizer error while building index: ${ex.getMessage}")
      }
    }
  }
}
