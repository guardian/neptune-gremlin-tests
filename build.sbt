ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.13"

lazy val root = (project in file("."))
  .settings(
    name := "neptune-gremlin-tests"
  )

libraryDependencies ++= Seq(
  "org.apache.tinkerpop" % "gremlin-driver" % "3.6.5",
  "org.apache.tinkerpop" % "gremlin-groovy" % "3.6.5",
  "org.slf4j" % "slf4j-jdk14" % "1.7.25",

)