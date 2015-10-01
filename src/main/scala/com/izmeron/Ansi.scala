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

object Ansi {

  def green(msg: String) = withAnsiCode(Console.GREEN, msg)
  def red(msg: String) = withAnsiCode(Console.RED, msg)
  def blue(msg: String) = withAnsiCode(Console.BLUE, msg)

  def blueMessage(msg: String) =
    withAnsiCode(s"${Console.BLUE}${Console.BOLD}", msg)

  def errorMessage(msg: String) =
    withAnsiCode(s"${Console.RED}${Console.BOLD}", msg)

  def highlightTweet(msg: String): String = {
    msg.
      replaceAll("(@[^\\s]+)", s"${Console.GREEN}$$1${Console.RESET}").
      replaceAll("(#[^\\s]+)", s"${Console.CYAN}$$1${Console.RESET}")
  }

  def withAnsiCode(in: String, msg: String) = s"$in$msg${Console.RESET}"
}
