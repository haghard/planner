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

import akka.actor.ActorSystem
import akka.stream.{ ActorMaterializer, ActorMaterializerSettings }
import akka.stream.scaladsl.{ Source, Merge, FlowGraph }
import com.typesafe.config.ConfigFactory
import org.specs2.mutable.Specification
import scala.concurrent.Await

class AkkaSpec extends Specification {
  val lenghtThreshold = 1800
  val minLenght = 400

  val dispCfg = ConfigFactory.parseString(
    """
      |akka {
      |  flow-dispatcher {
      |    type = Dispatcher
      |    executor = "fork-join-executor"
      |    fork-join-executor {
      |      parallelism-min = 4
      |      parallelism-max = 8
      |    }
      |  }
      |  blocking-dispatcher {
      |    executor = "thread-pool-executor"
      |    thread-pool-executor {
      |      core-pool-size-min = 4
      |      core-pool-size-max = 4
      |    }
      |  }
      |}
    """.stripMargin)

  implicit val system: ActorSystem = ActorSystem("Planner-test-System", ConfigFactory.empty().withFallback(dispCfg))
  val Settings = ActorMaterializerSettings(system)
    .withInputBuffer(initialSize = 64, maxSize = 64)
    .withDispatcher("akka.flow-dispatcher")

  implicit val materializer = ActorMaterializer(Settings)
  implicit val dispatcher = system.dispatchers.lookup("akka.flow-dispatcher")

  def inner(order: Order): akka.stream.scaladsl.Source[String, Unit] = Source(List("a", "b", "c", "d", "e", "f", "g")) //(dispatcher))

  val outer: akka.stream.scaladsl.Source[Order, Unit] =
    Source(() ⇒ List(Order("a", 1), Order("b", 3), Order("c", 5), Order("d", 7), Order("e", 1), Order("f", 1)).iterator)

  def mapSource = outer.grouped(4).map { batch ⇒
    Source() { implicit b ⇒
      import FlowGraph.Implicits._
      val innerSource = batch.map(order ⇒ inner(order).map(_.mkString(":")))
      val merge = b.add(Merge[String](batch.size))
      innerSource.foreach(_ ~> merge)
      merge.out
    }
  }.flatten(akka.stream.scaladsl.FlattenStrategy.concat[String])

  "Akka flow" should {
    "scenario0" in {
      import scala.concurrent.duration._
      val r = Await.result(mapSource.runForeach { m ⇒ println(m) }, 5 seconds)
      println(r)
      1 === 1
    }
  }
}
