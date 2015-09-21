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

import com.izmeron.http.RestServer

import scalaz.concurrent.Task
import org.apache.log4j.Logger
import java.util.concurrent.CountDownLatch

import com.typesafe.config.ConfigFactory

object Application extends App {
  import com.izmeron._

  val Path = "./cvs/metal2pipes2.csv"
  val OrderPath = "./cvs/order.csv"
  val logger = Logger.getLogger("scalaz-stream-csv")
  val LoggerSink = scalaz.stream.sink.lift[Task, (String, Int)](d ⇒ Task.delay(logger.debug(d)))
  val LoggerSink2 = scalaz.stream.sink.lift[Task, Iterable[Result]](list ⇒ Task.delay(logger.debug(s"approximation 2: $list")))

  //args
  val latch = new CountDownLatch(1)
  val cfg = ConfigFactory.load()

  val lenghtThreshold = cfg.getConfig("planner.distribution").getInt("lenghtThreshold")
  val minLenght = cfg.getConfig("planner.distribution").getInt("minLenght")
  val httpPort = cfg.getConfig("planner").getInt("httpPort")
  val path = cfg.getConfig("planner").getString("indexFile")

  val csvSource = scalaz.stream.csv.rowsR[RawCsvLine](Path, ';')
    .map { raw ⇒
      Etalon(raw.kd, raw.name, raw.nameMat, raw.marka,
        raw.diam.replaceAll(cvsSpace, empty).toInt,
        raw.len.replaceAll(cvsSpace, empty).toInt,
        raw.indiam.replaceAll(cvsSpace, empty).toInt,
        raw.numOptim.replaceAll(cvsSpace, empty).toInt,
        raw.numMin.replaceAll(cvsSpace, empty).toInt,
        raw.lenMin.replaceAll(cvsSpace, empty).toInt,
        raw.numSect.replaceAll(cvsSpace, empty).toInt,
        raw.numPart.replaceAll(cvsSpace, empty).toInt,
        raw.techComp.replaceAll(cvsSpace, empty).toInt)
    }

  val server = new RestServer(path, httpPort, org.apache.log4j.Logger.getLogger("planner-server"),
    minLenght, lenghtThreshold, Version(0, 0, 1)) with Planner
  server.start

  Runtime.getRuntime.addShutdownHook(new Thread(new Runnable {
    def run = server.shutdown
  }))
}