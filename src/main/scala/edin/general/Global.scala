package edin.general

import java.io.File
import java.lang.management.ManagementFactory
import java.text.SimpleDateFormat
import java.util.Date

import scala.io.Source

object Global {

  /////////////////////////////////////////////////////////////////////////////////////////
  ////////////////////////////      FINDING PROJECT DIRECTORY  ////////////////////////////
  /////////////////////////////////////////////////////////////////////////////////////////

  val homeDir: String = System.getProperty("user.home")

  val projectDir: String = {

    val classDir:String = getClass.getProtectionDomain.getCodeSource.getLocation.getPath
    // jar file or classes dir

    var dir = new File(classDir)

    if(dir.isFile)
      dir = dir.getParentFile

    while( ! dir.list().contains("lib")){
      dir = dir.getParentFile
      if(dir == null)
        sys.error(s"parent dirs of $classDir don't contain 'lib' subdir")
    }
    dir.getAbsolutePath
  }

  lazy val gitCommit : String = {
    val process = java.lang.Runtime.getRuntime.exec("git rev-parse HEAD", null, new File(projectDir))
    val stream  = process.getInputStream
    val lines   = Source.fromInputStream(stream).getLines().toList
    stream.close()
    if(process.exitValue == 0)
      lines.head
    else
      "NO_COMMIT_ID"
  }

  def currentTimeHumanFormat : String =
    new SimpleDateFormat("HH:mm dd.MM.yyyy").format(new Date())

  def printProcessId(): Unit = {
    System.err.println()
    System.err.println("process identity  : "+ManagementFactory.getRuntimeMXBean.getName)
    System.err.println("project dir       : "+projectDir)
    System.err.println("time process info : "+currentTimeHumanFormat)
    System.err.println("program git stamp : "+gitCommit)
    System.err.println()
  }

  def printMessageWithTime(msg:String) : Unit =
    System.err.println(msg+" at "+currentTimeHumanFormat)

}

