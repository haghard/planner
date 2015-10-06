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
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.RouteResult.route2HandlerFlow
import akka.stream.ActorMaterializer

import scala.concurrent.Future

object http {

  object Server {
    def apply(port: Int)(implicit system: ActorSystem, mat: ActorMaterializer): Future[akka.http.scaladsl.Http.ServerBinding] = {
      val route =
        path("version") {
          get {
            complete {
              "Here's some data... or would be if we had data."
            }
          }
        } ~ path("orders") {
          post {
            complete {
              "Ask for orders."
            }
          }
        }

      Http().bindAndHandle(route2HandlerFlow(route), "127.0.0.1", port)
    }
  }
}
