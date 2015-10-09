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

import scala.util.{ Failure, Success }

object Application extends App with com.izmeron.Indexing {
  val cfgPath = "./cfg/planner.conf"

  override val cfg = ConfigFactory.parseFile(new File(cfgPath))
  val plannerCfg = cfg.getConfig("akka.settings")

  override val lenghtThreshold = plannerCfg.getInt("distribution.lenghtThreshold")
  override val minLenght = plannerCfg.getInt("distribution.minLenght")
  override val indexPath = plannerCfg.getString("indexPath")
  override val httpPort = plannerCfg.getInt("httpPort")

  (maxLengthCheck zip multiplicity).flatMap {
    case (max, errors) ⇒ createFileIndex.map(http.Server(httpPort, lenghtThreshold, minLenght, _))
  }.onComplete {
    case Success(r) ⇒
      system.log.info(s"Params IndexPath:$indexPath  MinLenght:$minLenght LenghtThreshold:$lenghtThreshold")
      system.log.info(s"★ ★ ★ ★ ★ ★  Http server has been started on 127.0.0.1:$httpPort ★ ★ ★ ★ ★ ★")
    case Failure(ex) ⇒
      system.log.info(s"★ ★ ★ ★ ★ ★ Couldn't run the server cause: ${ex.getMessage} ★ ★ ★ ★ ★ ★")
      system.terminate()
      System.exit(0)
  }
}