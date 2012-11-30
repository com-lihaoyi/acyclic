package plugin

import tools.nsc.Global
import tools.nsc.plugins.{Plugin, PluginComponent}

class SinjectPlugin(val global: Global) extends Plugin {

  val name = "Sinject"
  val description = "Automatically creates implicit parameters"

  val components = List[PluginComponent](
    new SinjectTransformer(this, global)
  )

}
