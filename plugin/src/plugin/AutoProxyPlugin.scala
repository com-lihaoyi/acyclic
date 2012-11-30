package plugin

import tools.nsc.Global
import tools.nsc.plugins.{Plugin, PluginComponent}

class AutoProxyPlugin(val global: Global) extends Plugin {


  val AutoproxyAnnotationClass = "autoproxy.annotation.proxy"

  val name = "autoproxy"
  val description = "support for the @proxy annotation"

  val components = List[PluginComponent](
    new GenerateSynthetics(this, global)
  )

}
