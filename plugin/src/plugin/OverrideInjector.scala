package plugin

import tools.nsc.Settings
import tools.nsc.plugins.PluginComponent
import tools.nsc.transform.{Transform, TypingTransformers}

import tools.nsc.symtab.Flags._
import tools.nsc.ast.TreeDSL
import tools.nsc.interpreter._


class OverrideInjector(val plugin: SinjectPlugin)
    extends PluginComponent
    with Transform
    with TypingTransformers
    with TreeDSL{

  val global = plugin.global
  import global._

  val runsAfter = List("typer")
  override val runsRightAfter = Some("typer")
  val phaseName = "overrideInjector"



  def newTransformer(unit: CompilationUnit) = new TypingTransformer(unit) {


    override def transform(tree: Tree): Tree =  tree match {

      /* add injected class members and constructor parameters */

      case vd @ DefDef(mods, name, tparams, vparamss, tpt, rhs)
        if name.containsName(plugin.prefix) =>

        val inheritedNonDeferred = for{
          parent <- vd.symbol.owner.info.parents
          member <- parent.members
          if member.name.toString.contains(plugin.prefix)
          if member.tpe == vd.symbol.tpe
          if !member.isDeferred
        } yield member


        if (inheritedNonDeferred.length == 0) vd
        else {
          vd.symbol.setFlag(OVERRIDE)
          treeCopy.DefDef(
            vd,
            mods | OVERRIDE,
            name,
            tparams,
            vparamss,
            tpt,
            rhs
          )
        }



      case x => super.transform {x}
    }
  }
}
