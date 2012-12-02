package plugin

import tools.nsc.{Settings, Global}
import tools.nsc.plugins.PluginComponent
import tools.nsc.transform.{Transform, TypingTransformers}
import tools.nsc.symtab.Flags._
import tools.nsc.ast.TreeDSL
import tools.nsc.interpreter._
import reflect.internal.{Flags, SymbolTable}
import reflect.ClassTag


class SinjectTransformer(plugin: SinjectPlugin, val global: Global)
    extends PluginComponent
    with Transform
    with TypingTransformers
    with TreeDSL{

  import global._

  val runsAfter = List[String]("typer")
  val phaseName = "sinject"

  def newTransformer(unit: CompilationUnit) = new TypingTransformer(unit) {




    //http://stackoverflow.com/questions/7866696/define-a-constructor-parameter-for-a-synthetic-class-in-a-scala-compiler-plugin

    override def transform(tree: Tree): Tree = tree match{
      case cd @ ClassDef(mods, className, tparams, impl) =>
        println("ClassDef " + className)

        val enclosingModules = for{
          symbol <- (unit.body.symbol.info.prefixChain :+ unit.body.symbol.info).reverse
          decl <- symbol.decls
          TypeRef(tpe, sym, Seq(singleType)) <- decl.typeOfThis.parents
          if sym.typeOfThis.toString == "sinject.Module[T]"
        } yield singleType

        println("enclosingModules " + enclosingModules)

        def newValDefs(mods: Long) = enclosingModules.filter(_ != cd.symbol.tpe)
                                         .map{ enclosing =>

          println("Injecting " + enclosing + " into " + cd.symbol.tpe)
          val valdef = ValDef(
            Modifiers(mods),
            "sinjected" + math.abs(enclosing.hashCode()),
            TypeTree(enclosing),
            EmptyTree
          )
          valdef.symbol

        }

        val constructorDefs = newValDefs(PARAMACCESSOR | PARAM)
        val memberDefs = newValDefs(PARAMACCESSOR | PRIVATE | LOCAL)
        openInterpreter(
          "oldV" -> impl.body -> "List[Tree]",
          "members" -> memberDefs -> "List[Tree]",
          "newConstr" -> constructorDefs -> "List[Tree]"
        )

        treeCopy.ClassDef(
          tree,
          mods,
          className,
          tparams,
          treeCopy.Template(
            impl,
            impl.parents,
            impl.self,
            impl.body.map(constructorTransform(constructorDefs)) ++ memberDefs
          )
        )

      case x => super.transform(x)
    }
    def constructorTransform(newValDefs: List[ValDef])(tree: Tree) = tree match {
      case dd @ DefDef(modifiers, name, tparams, vparamss, tpt, rhs)
        if name.toString == "<init>" =>
        println("DefDef")

        val newvparamss = vparamss match{
          case first :+ last
            if last.forall(_.mods.hasFlag(Flags.IMPLICIT)) =>
            first :+ (last ++ newValDefs)
          case _ => vparamss :+ newValDefs
        }

        treeCopy.DefDef(dd, modifiers, name, tparams, newvparamss, tpt, rhs)
        dd

      case x => x
    }
  }
/*
openInterpreter(
          "dd" -> dd -> "DefDef",
          "encl" -> enclosingModules -> "List[Type]"
        )
*/
  def openInterpreter(bind: ((String, Any), String)*){
    val repl = new ILoop
    repl.settings = new Settings
    repl.in = SimpleReader()

    repl.settings.Yreplsync.value = true

    repl.createInterpreter()

    repl.bind("global", global)
    repl.interpret("import global._")
    for(((name, value), tpe) <- bind){
      repl.bind(s"a$name", value: Any)
      repl.interpret(s"val $name = a$name.asInstanceOf[$tpe]")
    }
    repl.loop()
    repl.closeInterpreter()
  }
}
