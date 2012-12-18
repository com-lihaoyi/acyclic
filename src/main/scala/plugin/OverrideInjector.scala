package plugin

import tools.nsc.Settings
import tools.nsc.plugins.PluginComponent
import tools.nsc.transform.{Transform, TypingTransformers}

import tools.nsc.symtab.Flags._
import tools.nsc.ast.TreeDSL
import tools.nsc.interpreter._

/**
 * Fixes the usage of `override` for all synthetic members injected by the
 * plugin. This has to run after the "typer" phase, because information about
 * each class' inheritence tree is requied to resolve whether each method needs
 * `override` or not.
 */
class OverrideInjector(val plugin: SinjectPlugin)
extends PluginComponent
with Transform
with TypingTransformers{

  val global = plugin.global
  import global._

  val runsAfter = List("typer")
  val phaseName = "sinjectOverride"

  def newTransformer(unit: CompilationUnit) = new TypingTransformer(unit) {

    override def transform(tree: Tree): Tree =  tree match {
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
