package plugin

import tools.nsc.Settings
import tools.nsc.plugins.PluginComponent
import tools.nsc.transform.{Transform, TypingTransformers}

import tools.nsc.symtab.Flags._
import tools.nsc.ast.TreeDSL
import tools.nsc.interpreter._


class OverrideStripper(val plugin: SinjectPlugin)
    extends PluginComponent
    with Transform
    with TypingTransformers
    with TreeDSL{

  val global = plugin.global
  import global._

  val runsAfter = List("typer")
  override val runsRightAfter = Some("typer")
  val phaseName = "stripper"

  val prefix = "sinj$"

  def newTransformer(unit: CompilationUnit) = new TypingTransformer(unit) {

    override def transform(tree: Tree): Tree =  tree match {
      case vd @ ValDef(mods, name, tpt, rhs) =>
        val valdef = vd
        println("stripper: " +vd)
        vd

      case x =>
        super.transform(x)
    }
  }

}
