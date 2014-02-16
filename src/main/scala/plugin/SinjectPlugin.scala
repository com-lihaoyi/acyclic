package plugin

import tools.nsc.Global
import tools.nsc.plugins.{Plugin, PluginComponent}

import scala.collection._



class SinjectPlugin(val global: Global) extends Plugin {

  val name = "Sinject"
  val description = "Automatically creates implicit parameters"


  println("SinjectPlugin")
  val components = List[PluginComponent](
    new Transformer(this)
  )
}
