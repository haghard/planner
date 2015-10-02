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

import org.http4s.dsl._
import org.http4s.server.HttpService
import scodec.Codec
import com.izmeron.http._

object OrderService {
  import scalaz.stream.Process

  private val codec: Codec[String] = scodec.codecs.utf8
  private val encodeUtf = scodec.stream.encode.many(codec)
  private val decodeUtf = scodec.stream.decode.many(codec)
  private var aggregator: OrigamiAggregator = null

  def apply(aggregator: OrigamiAggregator): HttpService = {
    this.aggregator = aggregator
    service
  }

  private val service = HttpService {
    case req @ POST -> Root / "orders" ⇒
      Ok{
        for {
          bv <- req.body.map(_.toBitVector)
          srt <- decodeUtf.decode(bv)
        } yield srt
      }.chunked
  }
}
