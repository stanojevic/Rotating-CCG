package edin.general

import java.io.File
import java.io.IOException

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

  /////////////////////////////////////////////////////////////////////////////////////////
  ////////////////////////////      LOADING NATIVE LIBRARIES  /////////////////////////////
  /////////////////////////////////////////////////////////////////////////////////////////

  def loadLibraries() : Unit = {
    val libDir = s"$projectDir/lib"

    if(librariesLoaded)
      return
    else
      librariesLoaded = true

    addNativeLibraryDir(libDir)
  }
  private var librariesLoaded = false
  loadLibraries()

  /*
   * this is a super hacky code and might not work on every JVM
   * taken from https://stackoverflow.com/questions/39137175/dynamically-compiling-scala-class-files-at-runtime-in-scala-2-11/39139732?utm_medium=organic&utm_source=google_rich_qa&utm_campaign=google_rich_qa
   */
  @throws[IOException]
  private def addNativeLibraryDir(s: String): Unit = {
    try { // This enables the java.library.path to be modified at runtime
      // From a Sun engineer at http://forums.sun.com/thread.jspa?threadID=707176
      //
      val field = classOf[ClassLoader].getDeclaredField("usr_paths")
      field.setAccessible(true)
      val paths = field.get(null).asInstanceOf[Array[String]]
      var i = 0
      while ( {
        i < paths.length
      }) {
        if (s == paths(i)) return

        {
          i += 1; i - 1
        }
      }
      val tmp = new Array[String](paths.length + 1)
      System.arraycopy(paths, 0, tmp, 0, paths.length)
      tmp(paths.length) = s
      field.set(null, tmp)
      System.setProperty("java.library.path", System.getProperty("java.library.path") + File.pathSeparator + s)
    } catch {
      case _:IllegalAccessException =>
        throw new IOException("Failed to get permissions to set library path")
      case _:NoSuchFieldException =>
        throw new IOException("Failed to get field handle to set library path")
    }
  }

}

