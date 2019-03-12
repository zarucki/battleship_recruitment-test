name := "battleships_rest_recruitment_task"

version := "0.1"

scalaVersion := "2.12.8"

scalacOptions ++= Seq(
  "-deprecation",
  "-feature",
  "-Xlint",
  "-opt:l:inline",
  "-opt-inline-from:**",
  "-Ypartial-unification",
  "-language:higherKinds"
)

javacOptions ++= Seq("-Xlint")

val slf4jVersion = "1.7.25"
val logBackVersion = "1.2.3"
val scalaLoggingVersion = "3.9.2"
val scalaTestVersion = "3.0.5"
val circeVersion = "0.11.1"
val akkaHttpVersion = "10.1.7"

val slf4jApi = "org.slf4j" % "slf4j-api" % slf4jVersion
val logBackClassic = "ch.qos.logback" % "logback-classic" % logBackVersion
val scalaLogging = "com.typesafe.scala-logging" %% "scala-logging" % scalaLoggingVersion
val loggingStack = Seq(slf4jApi, logBackClassic, scalaLogging)

libraryDependencies ++= loggingStack

libraryDependencies ++= Seq(
  "org.scalactic" %% "scalactic" % scalaTestVersion,
  "org.scalatest" %% "scalatest" % scalaTestVersion % "test"
)

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-stream" % "2.5.20",
  "com.typesafe.akka" %% "akka-testkit" % "2.5.20" % Test,
  "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % Test,
  "de.heikoseeberger" %% "akka-http-circe" % "1.25.2"
)

libraryDependencies ++= Seq(
  "io.circe" %% "circe-core",
  "io.circe" %% "circe-generic",
  "io.circe" %% "circe-parser"
).map(_ % circeVersion withSources () withJavadoc ())
