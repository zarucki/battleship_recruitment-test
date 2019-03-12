name := "battleships_rest_recruitment_task"

version := "0.1"

scalaVersion := "2.12.8"

scalacOptions ++= Seq("-deprecation", "-feature", "-Xlint", "-opt:l:inline", "-opt-inline-from:**", "-Ypartial-unification", "-language:higherKinds")

javacOptions ++= Seq("-Xlint")

val scalaTestVersion = "3.0.5"
val log4jVersion = "2.11.2"
val circeVersion = "0.11.1"

libraryDependencies ++= Seq(
	"org.scalactic" %% "scalactic" % scalaTestVersion,
	"org.scalatest" %% "scalatest" % scalaTestVersion % "test"
)

libraryDependencies ++= Seq(
	"org.apache.logging.log4j" % "log4j-slf4j-impl" % log4jVersion,
	"org.apache.logging.log4j" % "log4j-api" % log4jVersion,
	"org.apache.logging.log4j" % "log4j-core" % log4jVersion,
	"org.apache.logging.log4j" %% "log4j-api-scala" % "11.0"
)

libraryDependencies ++= Seq(
	"com.typesafe.akka" %% "akka-http"   % "10.1.7",
	"com.typesafe.akka" %% "akka-stream" % "2.5.20",
	"de.heikoseeberger" %% "akka-http-circe" % "1.25.2",
)

libraryDependencies ++= Seq(
	"io.circe" %% "circe-core" ,
	"io.circe" %% "circe-generic",
	"io.circe" %% "circe-parser"
).map(_ % circeVersion withSources() withJavadoc())
