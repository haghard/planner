import com.typesafe.sbt.SbtScalariform.ScalariformKeys
import scalariform.formatter.preferences._
//import bintray.Keys._
//import NativePackagerKeys._

organization := "com.izmeron"

name := "planner"

version := "0.0.3-SNAPSHOT"

scalaVersion := "2.12.13"

val Akka = "2.5.23"

parallelExecution := false
Test / parallelExecution := false
Test / logBuffered := false


console / initialCommands := "import org.specs2._"

Test / initialCommands := "import org.specs2._"

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
  "-target:jvm-1.8"
)

//useJGit
//enablePlugins(GitVersioning)
//enablePlugins(JavaAppPackaging)

Compile / mainClass := Some("com.izmeron.Application")

val localMvnRepo = "/Volumes/Data/dev_build_tools/apache-maven-3.1.1/repository"

//scalariformSettings

import scalariform.formatter.preferences._

scalariformPreferences := scalariformPreferences.value
  .setPreference(AlignSingleLineCaseStatements, true)
  .setPreference(DoubleIndentConstructorArguments, true)
  .setPreference(DanglingCloseParenthesis, Preserve)

/*ScalariformKeys.preferences := ScalariformKeys.preferences.value
  .setPreference(RewriteArrowSymbols, true)
  .setPreference(AlignParameters, true)
  .setPreference(AlignSingleLineCaseStatements, true)*/

//net.virtualvoid.sbt.graph.Plugin.graphSettings

resolvers ++= Seq(
  //"Local Maven Repository" at "file:///" + localMvnRepo,
  "maven central" at "https://repo.maven.apache.org/maven2",
  //"jboss repo"             at "http://repository.jboss.org/nexus/content/groups/public-jboss/"
)

libraryDependencies ++= Seq(
  "io.spray" %% "spray-json" % "1.3.2",

  "org.scala-sbt" %% "completion" % "1.5.5",

  //https://github.com/radicalbit/NSDb/blob/ef4fbeacca0d785fb5374fcc93cc8dc0a3d14582/nsdb-cli/src/main/scala/io/radicalbit/nsdb/cli/NsdbCli.scala
  "com.github.scopt" %% "scopt" % "3.7.1",

  "info.folone" %% "poi-scala" % "0.17",
  "com.typesafe.akka" %% "akka-stream" % Akka,
  "com.typesafe.akka" %% "akka-slf4j" % Akka,
  "org.typelevel" %% "simulacrum" % "1.0.0",
  "ch.qos.logback" % "logback-classic" % "1.1.2"
)

libraryDependencies ++= Seq(
  "org.specs2"        %%  "specs2-core"         %   "3.8.6"   % "test" withSources(),
  "org.scalatest"     %% "scalatest"            %   "3.0.5"   % "test",
  "org.scalacheck"    %%  "scalacheck"          %   "1.15.4"  % "test",
  "com.typesafe.akka" %% "akka-testkit" % Akka  %   "test",
  "org.mockito" % "mockito-core" % "1.10.19"
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

//seq(bintraySettings:_*)

licenses += ("Apache-2.0", url("http://www.apache.org/licenses/"))

//bintrayOrganization in bintray := Some("haghard")
//repository in bintray := "snapshots" //"releases"

//publishMavenStyle := true
//publishTo := Some(Resolver.file("file",  new File(localMvnRepo)))

//sbt createHeaders
/*headers := Map(
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
)*/

addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.1" cross CrossVersion.full)

//create/update for Compile and Test configurations, add the following settings to your build
//inConfig(Compile)(compileInputs.in(compile) <<= compileInputs.in(compile).dependsOn(createHeaders.in(compile)))
//inConfig(Test)(compileInputs.in(compile) <<= compileInputs.in(compile).dependsOn(createHeaders.in(compile)))

//To create a staging version of your package call
//sbt stage

//bintray:: tab
//bintray::publish