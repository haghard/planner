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

import sbt.complete.Parser
import sbt.complete.DefaultParsers._
import com.typesafe.config.ConfigFactory

import scala.annotation.tailrec

object Application extends App {

  val cfg = ConfigFactory.load()
  val lenghtThreshold = cfg.getConfig("planner.distribution").getInt("lenghtThreshold")
  val minLenght = cfg.getConfig("planner.distribution").getInt("minLenght")
  val httpPort = cfg.getConfig("planner").getInt("httpPort")
  val path = cfg.getConfig("planner").getString("indexFile")
  val outputDir = cfg.getConfig("planner").getString("outputDir")

  val outFormatJ = "json"
  val outFormatE = "excel"

  /*
  val server = new com.izmeron.http.PlannerServer(path, httpPort,
    org.apache.log4j.Logger.getLogger("planner-server"),
    minLenght, lenghtThreshold, coefficient, Version(0, 1, 0)) with Planner
  server.start()

  Runtime.getRuntime.addShutdownHook(new Thread(new Runnable {
    def run() = server.shutdown()
  }))
  */

  parseLine(args.mkString(" "), cliParser).fold(runCli()) { _.start() }

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

  def runCli(): Unit = {
    def readCommand(): Option[CliCommand] = readLine(cliParser)
    @tailrec def loop(): Unit = {
      val c = readCommand()
      print(s"${Ansi.blueMessage("--RUN-- ")}")
      c match {
        case None ⇒
          println(s"${Ansi.green("Unknown command: Please use [exit, check, plan]")}")
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

  def cliParser: Parser[CliCommand] = {
    import OutputWriter._
    val pathLineParser = any.* map (_.mkString(""))
    val exit = token("exit" ^^^ Exit)
    val check = (token("check" ~ Space) ~> pathLineParser).map(args ⇒ StaticCheck(args, minLenght, lenghtThreshold))
    val plan = (token("plan" ~ Space) ~> pathLineParser ~ (token("--out" ~ Space) ~> (outFormatJ | outFormatE))).map { args ⇒
      val path = args._1.trim
      val format = args._2.trim
      if (format == outFormatJ) Plan[spray.json.JsObject](path, outputDir, format, minLenght, lenghtThreshold)
      else Plan[Set[info.folone.scala.poi.Row]](path, outputDir, format, minLenght, lenghtThreshold)
    }

    exit | plan | check
  }
}