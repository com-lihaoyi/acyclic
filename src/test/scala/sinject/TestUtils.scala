package sinject

import reflect._
import io.VirtualDirectory
import tools.nsc.io._
import tools.nsc.{Global, Settings}
import tools.nsc.reporters.ConsoleReporter
import tools.nsc.plugins.Plugin
import plugin.SinjectPlugin
import java.net.URLClassLoader
import scala.tools.nsc.util.ClassPath

/**
 * Created with IntelliJ IDEA.
 * User: Haoyi
 * Date: 12/13/12
 * Time: 11:53 PM
 * To change this template use File | Settings | File Templates.
 */
object TestUtils {


  def getFilePaths(src: String): List[String] = {
    val f = new java.io.File(src)
    if (f.isDirectory) f.list.toList.flatMap(x => getFilePaths(src + "/" + x))
    else List(src)
  }

  /* Instantiates an object of type T passing the given arguments to its first constructor */
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

    lazy val compiler = new Global(settings, new ConsoleReporter(settings)){
      override protected def loadRoughPluginsList(): List[Plugin] = List(new SinjectPlugin(this))
    }
    val run = new compiler.Run()
    run.compile(sources)

    if (vd.toList.isEmpty) throw CompilationException
  }
  object CompilationException extends Exception
}
