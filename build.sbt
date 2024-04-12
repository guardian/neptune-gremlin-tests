ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.13"

lazy val root = (project in file("."))
  .settings(
    name := "neptune-gremlin-tests"
  )

val circeVersion = "0.14.1"

libraryDependencies ++= Seq(
  "io.circe" %% "circe-core",
  "io.circe" %% "circe-generic",
  "io.circe" %% "circe-parser"
).map(_ % circeVersion)

libraryDependencies ++= Seq(
  "org.apache.tinkerpop" % "gremlin-driver" % "3.6.5",
  "org.apache.tinkerpop" % "gremlin-groovy" % "3.6.5",
  "org.slf4j" % "slf4j-jdk14" % "1.7.25",
  "com.softwaremill.sttp.client4" %% "core" % "4.0.0-M11"
)