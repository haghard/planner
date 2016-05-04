import com.typesafe.sbt.SbtScalariform.ScalariformKeys
import scalariform.formatter.preferences._
import bintray.Keys._
import NativePackagerKeys._

organization := "com.izmeron"

name := "planner"

version := "0.0.2-snapshot"

scalaVersion := "2.11.7"

parallelExecution := false
parallelExecution in Test := false
logBuffered in Test := false

initialCommands in console in Test := "import org.specs2._"

shellPrompt := { state => System.getProperty("user.name") + "> " }

scalacOptions ++= Seq(
  "-feature",
  "-deprecation",
  "-language:implicitConversions",
  "-language:higherKinds",
  "-language:existentials",
  "-language:postfixOps",
  "-language:reflectiveCalls",
  "-Yno-adapted-args",
  "-target:jvm-1.7"
)

useJGit
enablePlugins(GitVersioning)
enablePlugins(JavaAppPackaging)

val Akka = "2.4.4"
mainClass in Compile := Some("com.izmeron.Application")

val localMvnRepo = "/Volumes/Data/dev_build_tools/apache-maven-3.1.1/repository"

scalariformSettings

ScalariformKeys.preferences := ScalariformKeys.preferences.value
  .setPreference(RewriteArrowSymbols, true)
  .setPreference(AlignParameters, true)
  .setPreference(AlignSingleLineCaseStatements, true)

net.virtualvoid.sbt.graph.Plugin.graphSettings

resolvers ++= Seq(
  "Local Maven Repository" at "file:///" + localMvnRepo,
  "maven central"          at "http://repo.maven.apache.org/maven2",
  "jboss repo"             at "http://repository.jboss.org/nexus/content/groups/public-jboss/"
)

libraryDependencies ++= Seq(
  "io.spray"            %%  "spray-json"               %   "1.3.2",
  "info.folone"         %%  "poi-scala"                %   "0.15",
  "com.typesafe.akka"   %%  "akka-stream"              %   Akka,
  "com.typesafe.akka"   %%  "akka-http-core"           %   Akka,
  "com.typesafe.akka"   %%  "akka-http-experimental"   %   Akka,
  "com.typesafe.akka"   %%  "akka-slf4j"               %   Akka,
  "ch.qos.logback"      %   "logback-classic"          %   "1.1.2"
)

libraryDependencies ++= Seq(
  "org.specs2"        %%  "specs2-core"       %   "3.2"     % "test" withSources(),
  "org.scalatest"     %%  "scalatest"         %   "2.2.5"   % "test",
  "org.scalacheck"    %%  "scalacheck"        %   "1.12.4"  % "test"
)

scalacOptions ++= Seq(
  "-encoding", "UTF-8",
  "-target:jvm-1.8",
  "-deprecation",
  "-unchecked",
  "-Ywarn-dead-code",
  "-feature",
  "-language:implicitConversions",
  "-language:postfixOps",
  "-language:existentials")

javacOptions ++= Seq(
  "-source", "1.8",
  "-target", "1.8",
  "-Xlint:unchecked",
  "-Xlint:deprecation")

seq(bintraySettings:_*)

licenses += ("Apache-2.0", url("http://www.apache.org/licenses/"))

bintrayOrganization in bintray := Some("haghard")

repository in bintray := "snapshots" //"releases"

publishMavenStyle := true
//publishTo := Some(Resolver.file("file",  new File(localMvnRepo)))

cancelable in Global := true

//sbt createHeaders
headers := Map(
  "scala" -> (
    HeaderPattern.cStyleBlockComment,
   """|/*
      | * Licensed under the Apache License, Version 2.0 (the "License");
      | * you may not use this file except in compliance with the License.
      | * You may obtain a copy of the License at
      | *
      | *    http://www.apache.org/licenses/LICENSE-2.0
      | *
      | * Unless required by applicable law or agreed to in writing, software
      | * distributed under the License is distributed on an "AS IS" BASIS,
      | * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
      | * See the License for the specific language governing permissions and
      | * limitations under the License.
      | */
      |
      |""".stripMargin
    )
)


//create/update for Compile and Test configurations, add the following settings to your build
inConfig(Compile)(compileInputs.in(compile) <<= compileInputs.in(compile).dependsOn(createHeaders.in(compile)))
inConfig(Test)(compileInputs.in(compile) <<= compileInputs.in(compile).dependsOn(createHeaders.in(compile)))

//bintray:: tab
//bintray::publish