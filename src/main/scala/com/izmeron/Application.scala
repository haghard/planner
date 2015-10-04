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
import knobs._
import scalaz.concurrent.Task

object Application extends App {
  val cfgPath = "./cfg/planner.cfg"

  (allConfigs(List(knobs.Required(knobs.FileResource(new File(cfgPath))))) { cfg ⇒
    for {
      lenghtThreshold ← cfg.lookup[Int]("planner.distribution.lenghtThreshold")
      minLenght ← cfg.lookup[Int]("planner.distribution.minLenght")
      httpPort ← cfg.lookup[Int]("planner.httpPort")
      indexPath ← cfg.lookup[String]("planner.indexFile")
    } yield (lenghtThreshold, minLenght, httpPort, indexPath)
  }).attemptRun.fold({ ex ⇒ println(Ansi.red(ex.getMessage)); System.exit(0) }, { cfg ⇒
    import scalaz._, Scalaz._
    (for {
      th ← cfg._1 \/> "lenghtThreshold is missing in cfg"
      minL ← cfg._2 \/> "minLenght is missing in cfg"
      port ← cfg._3 \/> "httpPort is missing in cfg"
      index ← cfg._4 \/> "indexPath is missing in cfg"
    } yield (th, minL, port, index)).fold({ ex ⇒ println(Ansi.red(ex)); System.exit(0) }, { cfg ⇒
      val server = new com.izmeron.netty.Server(cfg._4, cfg._3,
        org.apache.log4j.Logger.getLogger("planner-server"),
        cfg._2, cfg._1, Version(0, 0, 1)) with OrigamiAggregator
      server.start()

      Runtime.getRuntime.addShutdownHook(new Thread(new Runnable {
        def run() = server.shutdown()
      }))
    })
  })

  def allConfigs[A](files: List[KnobsResource])(t: MutableConfig ⇒ Task[A]): Task[A] =
    for {
      mb ← load(files)
      r ← t(mb)
    } yield r
}