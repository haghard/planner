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
import knobs.{ MutableConfig, KnobsResource }

import scalaz.concurrent.Task

object Application extends App {
  val cfgPath = "./cfg/planner.cfg"
  val log = org.apache.log4j.Logger.getLogger("planner")

  (allConfigs(List(knobs.Required(knobs.FileResource(new File(cfgPath))))) { cfg ⇒
    for {
      lenghtThreshold ← cfg.lookup[Int]("planner.distribution.lenghtThreshold")
      minLenght ← cfg.lookup[Int]("planner.distribution.minLenght")
      httpPort ← cfg.lookup[Int]("planner.httpPort")
      index ← cfg.lookup[String]("planner.indexFile")
    } yield (lenghtThreshold, minLenght, httpPort, index)
  }).attemptRun.fold({ ex ⇒ println(ex.getMessage); System.exit(0) }, { cfg ⇒
    import scalaz._, Scalaz._
    (for {
      th ← cfg._1 \/> "lenghtThreshold is missing in cfg"
      minL ← cfg._2 \/> "minLenght is missing in cfg"
      httpPort ← cfg._3 \/> "httpPort is missing in cfg"
      index ← cfg._4 \/> "indexFile is missing in cfg"
    } yield (th, minL, httpPort, index)).fold({ ex ⇒ println(ex); System.exit(0) }, { cfg ⇒
      new http.Server(cfg._4, cfg._3, log, cfg._2, cfg._1, Version(0, 0, 1)).start()
    })
  })

  def allConfigs[A](files: List[KnobsResource])(t: MutableConfig ⇒ Task[A]): Task[A] =
    for {
      mb ← knobs.load(files)
      r ← t(mb)
    } yield r
}