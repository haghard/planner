import com.typesafe.sbt.SbtScalariform.ScalariformKeys
import scalariform.formatter.preferences._
import bintray.Keys._
import NativePackagerKeys._

organization := "com.izmeron"

name := "planner"

version := "0.0.1-snapshot"

scalaVersion := "2.11.6"

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

mainClass in Compile := Some("com.izmeron.Application")

val Origami = "1.0-20150902134048-8d00462"
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
  "Scalaz"                 at "http://dl.bintray.com/scalaz/releases",
  "jboss repo"             at "http://repository.jboss.org/nexus/content/groups/public-jboss/",
  "oncue.bintray"          at "http://dl.bintray.com/oncue/releases"
)

//https://dl.bintray.com/oncue/releases/oncue/knobs/core_2.11/3.3.3/
libraryDependencies ++= Seq(
  "log4j"               %   "log4j"                   %   "1.2.14",
  "oncue.knobs"         %%  "core"                    %   "3.3.3",
  "io.spray"            %%  "spray-json"              %   "1.3.2",
  "com.nrinaudo"        %%  "scalaz-stream-csv"       %   "0.1.3",
  "org.scala-sbt"       %   "completion"              %   "0.13.9",
  "info.folone"         %%  "poi-scala"               %   "0.15",
  "com.ambiata"         %%  "origami-core"            %   Origami,
  "com.ambiata"         %%  "origami-stream"          %   Origami  exclude("com.google.caliper","caliper") exclude("com.google.guava", "guava")
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