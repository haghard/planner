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

import com.typesafe.config.ConfigFactory
import sbt.complete.Parser
import sbt.complete.DefaultParsers._
import com.izmeron.out.{ ExcelOutputModule, JsonOutputModule }
import com.izmeron.commands.{ Exit, Plan, StaticCheck, CliCommand }

import scala.annotation.tailrec

object Application extends App {
  val outFormatJ = "json"
  val outFormatE = "excel"
  val cfgPath = "./cfg/planner.conf"

  val cfg = ConfigFactory.parseFile(new File(cfgPath))

  val plannerCfg = cfg.getConfig("akka.settings")
  val lenghtThreshold = plannerCfg.getInt("distribution.lenghtThreshold")
  val minLenght = plannerCfg.getInt("distribution.minLenght")
  val outputDir = plannerCfg.getString("outputDir")

  println(Ansi.green(s"Planner has been started with lenghtThreshold:$lenghtThreshold minLenght:$minLenght outputDir:$outputDir"))
  parseLine(args.mkString(" "), cliParser(lenghtThreshold, minLenght, outputDir)).fold(runCli(lenghtThreshold, minLenght, outputDir)) { _.start() }

  private def readLine[U](parser: Parser[U], prompt: String = "> ", mask: Option[Char] = None): Option[U] = {
    val reader = new sbt.FullReader(None, parser)
    reader.readLine(prompt, mask) flatMap { line ⇒
      parseLine(line, parser)
    }
  }

  private def parseLine[U](line: String, parser: Parser[U]): Option[U] = {
    val parsed = Parser.parse(line, parser)
    parsed match {
      case Right(value) ⇒ Some(value)
      case Left(e)      ⇒ None
    }
  }

  def runCli(lenghtThreshold: Int, minLenght: Int, outputDir: String): Unit = {
    def readCommand(): Option[CliCommand] = readLine(cliParser(lenghtThreshold, minLenght, outputDir))
    @tailrec def loop(): Unit = {
      val c = readCommand()
      print(s"${Ansi.blueMessage("--RUN-- ")}")
      c match {
        case None ⇒
          println(s"${Ansi.green("Unknown command: Please use commands [exit, check, plan]")}")
          loop()
        case Some(Exit) ⇒
          println(s"${Ansi.green("exit")}")
          System.exit(0)
        case Some(cmd) ⇒ {
          println(s"${Ansi.green(cmd.getClass.getName)}")
          cmd.start()
          loop()
        }
      }
    }
    loop()
  }

  def cliParser(lenghtThreshold: Int, minLenght: Int, outputDir: String): Parser[CliCommand] = {
    val pathLineParser = any.* map (_.mkString(""))
    val exit = token("exit" ^^^ Exit)
    val check = (token("check" ~ Space) ~> pathLineParser).map(args ⇒ StaticCheck(args, minLenght, lenghtThreshold))
    val plan = (token("plan" ~ Space) ~> pathLineParser ~ (token("--out" ~ Space) ~> (outFormatJ | outFormatE))).map { args ⇒
      val path = args._1.trim
      val format = args._2.trim
      if (format == outFormatJ) Plan[JsonOutputModule](path, outputDir, format, minLenght, lenghtThreshold)
      else Plan[ExcelOutputModule](path, outputDir, format, minLenght, lenghtThreshold)
    }
    exit | plan | check
  }
}