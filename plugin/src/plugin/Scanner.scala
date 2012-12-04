package plugin

import tools.nsc.{Settings, Global}
import tools.nsc.plugins.PluginComponent
import tools.nsc.transform.{Transform, TypingTransformers}
import tools.nsc.symtab.Flags._
import tools.nsc.ast.TreeDSL
import tools.nsc.interpreter._
import reflect.internal.Flags


class Scanner(val plugin: SinjectPlugin)
    extends PluginComponent
    with Transform
    with TypingTransformers{


  val global = plugin.global
  import global._

  val runsAfter = List("parser")
  override val runsRightAfter = Some("parser")

  val phaseName = "sinjectScanner"

  def newTransformer(unit: CompilationUnit) = new TypingTransformer(unit){
    override def transform(tree: Tree): Tree = {
      tree
    }

  }
}
