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

import java.io.File

import com.izmeron.out.{OutputWriter, OutputModule}

import scalaz.concurrent.Task
import scalaz.effect.IO
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
    val rule0 = "0. Max lenght rule"
    val rule1 = "1. Multiplicity rule"

    def apply(path: String, minLenght: Int, lenghtThreshold: Int) = new StaticCheck(path, minLenght, lenghtThreshold)
  }

  final class StaticCheck(override val indexPath: String, override val minLenght: Int,
                          override val lenghtThreshold: Int) extends CliCommand with OrigamiAggregator {
    import StaticCheck._
    override val log = org.apache.logging.log4j.LogManager.getLogger("static-check")

    override def start() = {
      maxLengthCheck.attemptRun.fold({ th: Throwable ⇒ println(s"${Ansi.errorMessage(th.getMessage)}") }, {
        case ((\/-(elem), None)) ⇒
          elem.fold(println(s"${Ansi.errorMessage("Can't perform check")}")) { maxLenElem ⇒
            if (lenghtThreshold < maxLenElem.len) {
              log.debug("{}: Config value {} but {} has been found", rule0, lenghtThreshold.toString, maxLenElem.toString)
              println(s"${Ansi.blueMessage(rule0)}: ${Ansi.errorMessage(s"Config value $lenghtThreshold but $maxLenElem has been found")}")
            } else {
              log.debug("{}: {}", rule0, ok)
              println(s"${Ansi.blueMessage(rule0)}: ${Ansi.blueMessage(ok)}")
            }
          }
        case ((-\/(ex)), None) ⇒
          println(s"${Ansi.blueMessage(rule0)}: ${Ansi.errorMessage(ex.getMessage)}")
        case other             ⇒
          println(s"${Ansi.blueMessage(rule0)}: ${Ansi.errorMessage(other.toString())}")
      })

      multiplicityCheck.attemptRun.fold({ th: Throwable ⇒ println(s"${Ansi.errorMessage(th.getMessage)}") }, {
        case ((\/-(list), None)) ⇒
          if (list.isEmpty) {
            log.debug("{}: {}", rule1, ok)
            println(s"${Ansi.blueMessage(rule1)}: ${Ansi.blueMessage(ok)}")
          } else {
            log.debug("{}: {}: {}", rule1, list.size.toString, list.mkString(separator))
            println(s"${Ansi.blueMessage(rule1)}: ${Ansi.green(list.size.toString)}: ${Ansi.errorMessage(list.mkString(separator))}")
          }
        case ((-\/(ex)), None) ⇒
          log.debug("{}: {}", rule1, ex.getMessage)
          println(s"${Ansi.blueMessage(rule1)}: ${Ansi.errorMessage(ex.getMessage)}")
        case other             ⇒
          log.debug("{}: {}", rule1, other.toString())
          println(s"${Ansi.blueMessage(rule1)}: ${Ansi.errorMessage(other.toString())}")
      })
    }
  }

  object Plan {
    private val message = "Result has been written in file"
    implicit val ex = PlannerEx
    implicit val CpuIntensive = scalaz.concurrent.Strategy.Executor(PlannerEx)

    def apply[T <: OutputModule](path: String, outputDir: String, outFormat: String, minLenght: Int, lenghtThreshold: Int)
                                (implicit writer: OutputWriter[T]) =
      new Plan[T](path, outputDir, outFormat, minLenght, lenghtThreshold, writer)
  }

  final class Plan[T <: OutputModule](override val indexPath: String, outputDir: String, outFormat: String, override val minLenght: Int,
                                      override val lenghtThreshold: Int, writer: OutputWriter[T]) extends CliCommand
    with OrigamiAggregator with ScalazFlowSupport {
    import Plan._
    import scalaz.stream.merge
    import scalaz.stream.async

    override val log = org.apache.logging.log4j.LogManager.getLogger("planner")

    /**
     * Computation graph
     *
     * File           Parallel stage                                         Parallel stage
     * +----------+   +-------------+                                          +------------+
     * |csv_line0 |---|distribute   |--+                                  +----|cuttingStock|----+
     * +----------+   +-------------+  |  Fan-in stage                    |    +------------+    |
     * +----------+   +-------------+  |  +----------+  +-------------+   |    +------------+    |  +------------+   +-------+
     * |csv_line1 |---|distribute   |-----|foldMonoid|--|bounded queue|--------|cuttingStock|-------|monoidMapper|---|convert|
     * +----------+   +-------------+  |  +----------+  +-------------+   |    +------------+    |  +------------+   +-------+
     *                                 |                                  |    +------------+    |
     * +----------+   +-------------+  |                                  +----|cuttingStock|----+
     * |csv_line2 |---|distribute   |--+                                       +------------+
     * +----------+   +-------------+
     */
    override def start(): Unit = {
      createIndex.runAsync {
        case \/-((\/-(index), None)) ⇒ {
          log.debug("Index has been created")
          println(Ansi.green("Index has been created"))
          Task.fork {
            val queue = async.boundedQueue[List[Result]](Math.pow(parallelism, 2).toInt)
            (inputReader(orderReader map (distribute(_, index)), queue).drain merge merge.mergeN(parallelism)(cuttingStock(queue)))
              .foldMap(writer.monoidMapper(lenghtThreshold, _))(writer.monoid)
              .runLast
              .map(_.fold(writer.empty)(writer.convert))
          }.runAsync {
            case \/-(result) ⇒
              val fileName = s"plan_${System.currentTimeMillis}.${outFormat}"
              IO {
                val dir = new File(outputDir)
                if (!dir.exists())
                  dir.mkdirs()
                ()
              }.flatMap { _ =>
                (writer write(result, s"$outputDir/$fileName")).map { _ =>
                  val highlighted = fileName.replaceAll("(.+)", s"${Console.RED}$$1${Console.RESET}")
                  log.debug("{}: {}", message, s"$outputDir/$highlighted")
                  println(s"$message: $outputDir/$highlighted")
                }
              }.except { error =>
                IO {
                  log.debug("{}: {}: {}", error.getClass.getName, error.getStackTrace.mkString("\n"), error.getMessage)
                  println(Ansi.red(s"${error.getClass.getName}: ${error.getStackTrace.mkString("\n")}: ${error.getMessage}"))
                }
              }.unsafePerformIO()
            case -\/(error) ⇒
              println(Ansi.red(s"${error.getClass.getName}: ${error.getStackTrace.mkString("\n")}: ${error.getMessage}"))
              log.info(s"{}: {}: {}", error.getClass.getName, error.getStackTrace.mkString("\n"), error.getMessage)
          }
        }
        case -\/(ex) ⇒
          log.debug(s"{}: {}: {}", ex.getClass.getName, ex.getStackTrace.mkString("\n"), ex.getMessage)
          println(Ansi.red(s"${ex.getClass.getName}: ${ex.getStackTrace.mkString("\n")}: ${ex.getMessage}"))
        case \/-((-\/(ex), None)) ⇒
          log.debug(s"Error while building index: {}", ex.getMessage)
          println(Ansi.red(s"Error while building index: ${ex.getMessage}"))
        case \/-((_, Some(ex))) ⇒
          log.debug(s"Finalizer error while building index: {}", ex.getMessage)
          println(Ansi.red(s"Finalizer error while building index: ${ex.getMessage}"))
      }
    }
  }
}
