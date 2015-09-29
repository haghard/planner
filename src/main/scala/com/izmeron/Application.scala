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

import com.izmeron.http.PlannerServer
import com.typesafe.config.ConfigFactory

object Application extends App {

  val cfg = ConfigFactory.load()
  val lenghtThreshold = cfg.getConfig("planner.distribution").getInt("lenghtThreshold")
  val minLenght = cfg.getConfig("planner.distribution").getInt("minLenght")
  val httpPort = cfg.getConfig("planner").getInt("httpPort")
  val path = cfg.getConfig("planner").getString("indexFile")

  val server = new PlannerServer(path, httpPort, org.apache.log4j.Logger.getLogger("planner-server"),
    minLenght, lenghtThreshold, Version(0, 1, 0)) with Planner
  server.start

  Runtime.getRuntime.addShutdownHook(new Thread(new Runnable {
    def run = server.shutdown
  }))
}