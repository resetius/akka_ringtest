name := "RingBenchmark"

version := "0.1.0"

scalaVersion := "2.13.12"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.6.20",
  "com.typesafe.akka" %% "akka-remote" % "2.6.20",
  "io.altoo" %% "akka-kryo-serialization" % "2.5.0",
)

