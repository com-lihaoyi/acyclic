package acyclic.plugin

import tools.nsc.Global

class Plugin(val global: Global) extends tools.nsc.plugins.Plugin {

  val name = "Sinject"
  val description = "Automatically creates implicit parameters"

  println("SinjectPlugin")
  val components = List[tools.nsc.plugins.PluginComponent](
    new Transformer(this)
  )
}
