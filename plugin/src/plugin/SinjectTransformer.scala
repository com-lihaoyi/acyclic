package plugin

import tools.nsc.{Settings, Global}
import tools.nsc.plugins.PluginComponent
import tools.nsc.transform.{Transform, TypingTransformers}
import tools.nsc.symtab.Flags._
import tools.nsc.ast.TreeDSL
import tools.nsc.interpreter._
import reflect.internal.{Flags, SymbolTable}


class SinjectTransformer(plugin: SinjectPlugin, val global: Global) extends PluginComponent
        with Transform
        with TypingTransformers
        with TreeDSL
{
  import global._

  val runsAfter = List[String]("typer")
  val phaseName = "sinject"

  def newTransformer(unit: CompilationUnit) = new AutoProxyTransformer(unit)

  class AutoProxyTransformer(unit: CompilationUnit) extends TypingTransformer(unit) {

    val inserted =
          Select(
            Select(
              Select(
                Ident(
                  newTermName("_root_")
                ),
                newTermName("java")
              ),
              newTermName("lang")
            ),
            newTypeName("String")
          )

    //import CODE._
    println("Prefix Chain " + (unit.body.symbol.info.prefixChain :+ unit.body.symbol.info))
    val enclosingModule =
      (unit.body.symbol.info.prefixChain :+ unit.body.symbol.info).flatMap(
        (_: Type).decls.filter(
          (_: Symbol).annotations.exists(
            (_: AnnotationInfo).toString == "sinject.Module"
          )
        )
      ).headOption

    override def transform(tree: Tree): Tree = {
      val newTree =
        (tree, enclosingModule) match {
          case (cd @ ClassDef(mods, className, tparams, impl), Some(enclosing))
          if className.toString != enclosing.name.  toString =>
            val newBody = impl.body.map(_ match {
              case dd @ DefDef(modifiers, name, tparams, vparamss, tpt, rhs) if name.toString == "<init>" =>
                val newValDef = ValDef(Modifiers(IMPLICIT | PARAM | PARAMACCESSOR), "sinjected", inserted, EmptyTree)

                  treeCopy.DefDef(dd, modifiers, name, tparams, vparamss match{
                    case first :+ last if last.forall(_.mods.hasFlag(Flags.IMPLICIT)) =>
                      first :+ (last :+ newValDef)
                    case _ =>
                      vparamss :+ List(newValDef)
                  }, tpt, rhs)

              case x => x
            })
            treeCopy.ClassDef(tree, mods, className, tparams, treeCopy.Template(impl, impl.parents, impl.self, newBody))
          case _ => tree
        }
      super.transform(newTree)
    }
  }
}

/*
if (className.toString == "Cow"){
  val repl = new ILoop
  repl.settings = new Settings
  //repl.in = new JLineReader(null)
  repl.in = SimpleReader()

  // set the "-Yrepl-sync" option
  repl.settings.Yreplsync.value = true

  dd.vparamss(0)(0).mods.
  // start the interpreter and then close it after you :quit
  repl.createInterpreter()
  repl.bind("aunit", unit: Any)
  repl.bind("global", global)
  repl.interpret("import global._")
  repl.interpret("val unit = aunit.asInstanceOf[scala.tools.nsc.Global#CompilationUnit]")
  repl.bind("acd", cd: Any)
  repl.interpret("val cd = acd.asInstanceOf[scala.tools.nsc.Global#ClassDef]")

  repl.bind("add", dd: Any)
  repl.interpret("val dd = add.asInstanceOf[scala.tools.nsc.Global#DefDef]")
  repl.loop()
  repl.closeInterpreter()
}
          */