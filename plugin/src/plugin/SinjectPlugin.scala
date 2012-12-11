package plugin

import tools.nsc.Global
import tools.nsc.plugins.{Plugin, PluginComponent}

import scala.collection._



class SinjectPlugin(val global: Global) extends Plugin {

  val name = "Sinject"
  val description = "Automatically creates implicit parameters"

  var injections = Map[String, Set[String]]()

  val prefix = "sinj$"

  val components = List[PluginComponent](
    new OverrideInjector(this),
    new Transformer(this)

  )

}
