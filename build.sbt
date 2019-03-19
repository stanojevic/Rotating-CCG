name := "CCG-translator"
version := "0.1"
organization := "milos.unlimited"

scalaVersion := "2.12.6"

//// for Emacs ENSIME
// addSbtPlugin("org.ensime" % "sbt-ensime" % "2.1.0")

unmanagedBase := baseDirectory.value / "lib"

libraryDependencies += "org.scala-lang.modules" %% "scala-parser-combinators" % "1.1.1"

libraryDependencies += "com.github.wookietreiber" %% "scala-chart" % "latest.integration" // for plotting
libraryDependencies += "com.itextpdf" % "itextpdf" % "5.5.6" // so plotting can be done in pdf
libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.1" % "test" // for testing

libraryDependencies += "org.yaml" % "snakeyaml" % "1.8" // for yaml

libraryDependencies += "com.github.scopt" % "scopt_2.12" % "3.7.0" // for cmd line argument parsing

libraryDependencies += "io.spray" %%  "spray-json" % "1.3.3" // for parsing json and some python output

libraryDependencies += "org.apache.thrift" % "libthrift" % "0.11.0" // needed for communication with Python

libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.2.3" // required by thrift

test in assembly := {} // for skipping tests when assembling a fatjar

// Scala optimizations standard
// scalacOptions ++= Seq("-optimise")
