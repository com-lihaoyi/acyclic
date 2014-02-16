package acyclic

import reflect._
import io.VirtualDirectory
import tools.nsc.io._
import tools.nsc.{Global, Settings}
import tools.nsc.reporters.ConsoleReporter
import tools.nsc.plugins.Plugin

import java.net.URLClassLoader
import scala.tools.nsc.util.ClassPath

object TestUtils {
  def getFilePaths(src: String): List[String] = {
    val f = new java.io.File(src)
    if (f.isDirectory) f.list.toList.flatMap(x => getFilePaths(src + "/" + x))
    else List(src)
  }

  /**
   * Attempts to compile a resource folder as a compilation run, in order
   * to test whether it succeeds or fails correctly.
   */
  def make(path: String) = {


    val src = "src/test/resources/" + path.replace('.', '/')
    val sources = getFilePaths(src)

    val vd = new VirtualDirectory("(memory)", None)
    lazy val settings = new Settings
    val loader = getClass.getClassLoader.asInstanceOf[URLClassLoader]
    val entries = loader.getURLs map(_.getPath)
    settings.outputDirs.setSingleOutput(vd)

    // annoyingly, the Scala library is not in our classpath, so we have to add it manually
    val sclpath = entries.find(_.endsWith("scala-compiler.jar")).map(
      _.replaceAll("scala-compiler.jar", "scala-library.jar")
    )

    settings.classpath.value = ClassPath.join(entries ++ sclpath : _*)

    var cycles: Option[Seq[Seq[(String, Set[Int])]]] = None
    lazy val compiler = new Global(settings, new ConsoleReporter(settings)){
      override protected def loadRoughPluginsList(): List[Plugin] = {
        List(new plugin.Plugin(this, c => cycles = Some(c)))
      }
    }
    val run = new compiler.Run()
    run.compile(sources)

    if (vd.toList.isEmpty) throw CompilationException(cycles.get)
  }
  case class CompilationException(cycles: Seq[Seq[(String, Set[Int])]]) extends Exception
}
