package plugin

import tools.nsc.Global
import tools.nsc.plugins.{Plugin, PluginComponent}

class SinjectPlugin(val global: Global) extends Plugin {


  val AutoproxyAnnotationClass = "autoproxy.annotation.proxy"

  val name = "autoproxy"
  val description = "support for the @proxy annotation"

  val components = List[PluginComponent](
    new SinjectTransformer(this, global)
  )

}
