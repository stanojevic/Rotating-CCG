name := "CCG-translator"
version := "0.1"
organization := "milos.unlimited"

scalaVersion := "2.12.8"

unmanagedBase := baseDirectory.value / "lib"

resolvers += "jitpack" at "https://jitpack.io"

libraryDependencies += "org.scala-lang.modules" %% "scala-parser-combinators" % "1.1.1"
libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.1" % "test"

libraryDependencies += "com.github.wookietreiber" %% "scala-chart" % "latest.integration" // for plotting
libraryDependencies += "com.itextpdf" % "itextpdf" % "5.5.6" // so plotting can be done in pdf

libraryDependencies += "com.github.scopt" % "scopt_2.12" % "3.7.0" // for cmd line argument parsing
libraryDependencies += "org.yaml" % "snakeyaml" % "1.8"            // for yaml
libraryDependencies += "io.spray" %%  "spray-json" % "1.3.3"       // for json

libraryDependencies += "com.lihaoyi" %% "ammonite-ops" % "1.6.3"

// libraryDependencies += "org.jheaps" % "jheaps" % "0.10" // for Fibonacci heaps
libraryDependencies += "com.github.d-michail" % "jheaps" % "fd5c4c15ee" // jheaps version with support for heapify handlers

test in assembly := {}                // so assembly doesn't trigger unnecessary testing
logLevel  in assembly := Level.Error  // so assembly doesn't complain of multiple versions of same jars
mainClass in Compile  := None         // so assembly doesn't complain of having multiple main classes

scalacOptions ++= Seq("-deprecation", "-feature") // for more informative output of a compiler

// OPTIMIZATION
val optimized = List("OPTIMIZED", "OPTIMIZE").exists(x => sys.env.contains(x) && sys.env(x) == "true")
if(optimized){
  System.err.println("\n--- COMPILING OPTIMIZED VERSION ---\n")
  scalacOptions ++= Seq("-opt:l:inline", "-opt-inline-from:**", "-opt:l:method")
  scalacOptions  += "-opt-warnings:none"
}else{
  scalacOptions  += "-opt-warnings:none"
}

