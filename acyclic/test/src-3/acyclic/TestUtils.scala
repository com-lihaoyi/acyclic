package acyclic

import acyclic.plugin.Value
import java.io.OutputStream
import javax.print.attribute.standard.Severity
import scala.collection.SortedSet
import dotty.tools.io.{ClassPath, Path, PlainFile, VirtualDirectory}
import dotty.tools.dotc.Compiler
import dotty.tools.dotc.config.ScalaSettings
import dotty.tools.dotc.core.Contexts.{Context, ContextBase, FreshContext, NoContext}
import dotty.tools.dotc.interfaces.Diagnostic.{ERROR, INFO, WARNING}
import dotty.tools.dotc.plugins.Plugin
import dotty.tools.dotc.reporting.{ConsoleReporter, StoreReporter}
import java.net.URLClassLoader
import java.nio.file.Paths
import utest._
import utest.asserts._

object TestUtils extends BaseTestUtils {
  val srcDirName: String = "src-3"

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
    val sources = (getFilePaths(src) ++ extraIncludes).map(f => PlainFile(Path(Paths.get(f))))
    val vd = new VirtualDirectory("(memory)", None)
    val loader = getClass.getClassLoader.asInstanceOf[URLClassLoader]
    val entries = loader.getURLs map (_.getPath)

    val scalaSettings = new ScalaSettings {}
    val settingsState1 = scalaSettings.outputDir.updateIn(scalaSettings.defaultState, vd)
    val settingsState2 = scalaSettings.classpath.updateIn(settingsState1, ClassPath.join(entries*))

    val opts = List(
      if (force) Seq("force") else Seq(),
      if (warn) Seq("warn") else Seq()
    ).flatten

    val settingsState3 = if (opts.nonEmpty) {
      val options = opts.map("acyclic:" + _)
      println("options: " + options)
      scalaSettings.pluginOptions.updateIn(settingsState2, options)
    } else {
      settingsState2
    }

    var cycles: Option[Seq[Seq[(Value, SortedSet[Int])]]] = None
    val storeReporter = if (collectInfo) Some(new StoreReporter()) else None

    val ctxBase = new ContextBase {
      override val initialCtx: Context = FreshContext.initial(NoContext.base, settings)

      override protected def loadRoughPluginsList(using Context): List[Plugin] =
        List(new plugin.TestPlugin(foundCycles =>
          cycles = cycles match {
            case None => Some(Seq(foundCycles))
            case Some(oldCycles) => Some(oldCycles :+ foundCycles)
          }
        ))
    }

    given ctx: Context = FreshContext.initial(ctxBase, new ScalaSettings {
      override val defaultState = settingsState3
    })
      .asInstanceOf[FreshContext]
      .setReporter(storeReporter.getOrElse(ConsoleReporter()))

    ctx.initialize()

    val compiler = new Compiler()
    val run = compiler.newRun

    run.compile(sources)

    if (vd.toList.isEmpty) throw CompilationException(cycles.get)

    storeReporter.map(_.pendingMessages.toSeq.map(i => (i.msg.message, i.level match {
      case ERROR => "error"
      case INFO => "info"
      case WARNING => "warning"
    }))).getOrElse(Seq.empty)
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
