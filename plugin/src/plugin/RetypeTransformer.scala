package plugin

import tools.nsc.Global
import tools.nsc.plugins.PluginComponent
import tools.nsc.transform.{Transform, TypingTransformers}
import tools.nsc.symtab.Flags._
import tools.nsc.ast.TreeDSL
import reflect.internal.Flags


class RetypeTransformer(plugin: SinjectPlugin, val global: Global) extends PluginComponent
        with Transform
        with TypingTransformers
        with TreeDSL
{
  import global._

  val runsAfter = List[String]("sinject")
  val phaseName = "retype"

  def newTransformer(unit: CompilationUnit) = new Transformer{
    override def transformUnit(unit: CompilationUnit)= {
      global.typer.typed(unit.body)
    }
  }
}