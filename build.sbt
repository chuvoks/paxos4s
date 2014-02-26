name := "paxos"

organization := "paxos4s"

version := "0.9"

scalaVersion := "2.10.3"

javacOptions ++= Seq("-source", "1.7", "-target", "1.7")

scalacOptions ++= Seq("-unchecked", "-deprecation", "-Xlint")

EclipseKeys.createSrc := EclipseCreateSrc.Default + EclipseCreateSrc.Resource

EclipseKeys.executionEnvironment := Some(EclipseExecutionEnvironment.JavaSE17)

EclipseKeys.withSource := true

//logBuffered := false

//parallelExecution := false

testOptions in Test += Tests.Argument("-oD")

libraryDependencies ++= Seq(
  "org.slf4j" % "slf4j-api" % "1.7.6",
  "com.typesafe" %% "scalalogging-slf4j" % "1.0.1",
  "ch.qos.logback" % "logback-classic" % "1.0.13" % "test",
  "org.scalatest" % "scalatest_2.10" % "2.0" % "test",
  // "org.scalacheck" %% "scalacheck" % "1.10.1" % "test",
  "com.typesafe.akka" %% "akka-actor" % "2.2.3" % "test"
)
