//acyclic

package acyclic.plugin

import tools.nsc.Global

class Plugin(val global: Global, cycleReporter: Seq[Seq[(String, Set[Int])]] => Unit) extends tools.nsc.plugins.Plugin {

  val name = "Sinject"
  val description = "Automatically creates implicit parameters"

  val components = List[tools.nsc.plugins.PluginComponent](
    new PluginPhase(this.global, cycleReporter)
  )
}
