package acyclic.plugin

import acyclic.file
import dotty.tools.dotc.plugins.{PluginPhase, StandardPlugin}
import scala.collection.SortedSet
import dotty.tools.dotc.core.Contexts.Context

class RuntimePlugin extends TestPlugin()
class TestPlugin(cycleReporter: Seq[(Value, SortedSet[Int])] => Unit = _ => ()) extends StandardPlugin {

  val name = "acyclic"
  val description = "Allows the developer to prohibit inter-file dependencies"

  var force = false
  var fatal = true
  var alreadyRun = false

  private class Phase() extends PluginPhase {
    val phaseName = "acyclic"
    override val runsBefore = Set("patternMatcher")

    override def run(using Context): Unit = {
      if (!alreadyRun) {
        alreadyRun = true
        new acyclic.plugin.PluginPhase(cycleReporter, force, fatal).run()
      }
    }
  }

  override def init(options: List[String]): List[PluginPhase] = {
    if (options.contains("force")) {
      force = true
    }
    if (options.contains("warn")) {
      fatal = false
    }
    List(Phase())
  }
}
