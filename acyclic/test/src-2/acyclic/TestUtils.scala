package acyclic

import tools.nsc.{Global, Settings}
import tools.nsc.reporters.{ConsoleReporter, StoreReporter}
import tools.nsc.plugins.Plugin
import java.net.URLClassLoader
import scala.tools.nsc.util.ClassPath
import utest._
import asserts._

import scala.reflect.io.VirtualDirectory
import acyclic.plugin.Value

import java.io.OutputStream
import javax.print.attribute.standard.Severity
import scala.collection.SortedSet

object TestUtils extends BaseTestUtils {
  val srcDirName: String = "src-2"

  /**
   * Attempts to compile a resource folder as a compilation run, in order
   * to test whether it succeeds or fails correctly.
   */
  def make(
      path: String,
      extraIncludes: Seq[String] = Seq("acyclic/src/acyclic/package.scala"),
      force: Boolean = false,
      warn: Boolean = false,
      collectInfo: Boolean = true
  ): Seq[(String, String)] = {
    val src = "acyclic/test/resources/" + path
    val sources = getFilePaths(src) ++ extraIncludes

    val vd = new VirtualDirectory("(memory)", None)
    lazy val settings = new Settings
    val entries = getJavaClasspathEntries()
    settings.outputDirs.setSingleOutput(vd)

    println(s"entries: ${entries.toIndexedSeq}")

    // annoyingly, the Scala library is not in our classpath, so we have to add it manually
    val sclpath = entries.map(
      _.replaceAll("scala-compiler.jar", "scala-library.jar")
    )

    settings.classpath.value = ClassPath.join(entries ++ sclpath: _*)

    val opts = List(
      if (force) Seq("force") else Seq(),
      if (warn) Seq("warn") else Seq()
    ).flatten
    if (opts.nonEmpty) {
      val options = opts.map("acyclic:" + _)
      println("options: " + options)
      settings.pluginOptions.value = options
    }

    var cycles: Option[Seq[Seq[(acyclic.plugin.Value, SortedSet[Int])]]] = None
    val storeReporter = if (collectInfo) Some(new StoreReporter()) else None

    lazy val compiler = new Global(settings, storeReporter.getOrElse(new ConsoleReporter(settings))) {
      override protected def loadRoughPluginsList(): List[Plugin] = {
        List(new plugin.TestPlugin(
          this,
          foundCycles =>
            cycles = cycles match {
              case None => Some(Seq(foundCycles))
              case Some(oldCycles) => Some(oldCycles :+ foundCycles)
            }
        ))
      }
    }
    val run = new compiler.Run()
    run.compile(sources)

    if (vd.toList.isEmpty) throw CompilationException(cycles.get)

    storeReporter.map(_.infos.toSeq.map(i => (i.msg, i.severity.toString))).getOrElse(Seq.empty)
  }

  def makeFail(path: String, force: Boolean = false)(expected: Seq[(Value, SortedSet[Int])]*) = {
    def canonicalize(cycle: Seq[(Value, SortedSet[Int])]): Seq[(Value, SortedSet[Int])] = {
      val startIndex = cycle.indexOf(cycle.minBy(_._1.toString))
      cycle.toList.drop(startIndex) ++ cycle.toList.take(startIndex)
    }

    val ex = intercept[CompilationException] { make(path, force = force, collectInfo = false) }
    val cycles = ex.cycles
      .map(canonicalize)
      .map(
        _.map {
          case (Value.File(p, pkg), v) => (Value.File(p, Nil), v)
          case x => x
        }
      )
      .toSet

    def expand(v: Value) = v match {
      case Value.File(filePath, pkg) => Value.File("acyclic/test/resources/" + path + "/" + filePath, Nil)
      case v => v
    }

    val fullExpected = expected.map(_.map(x => x.copy(_1 = expand(x._1))))
      .map(canonicalize)
      .toSet

    assert(fullExpected.forall(cycles.contains))
  }

}
