package plugin

import tools.nsc.{Settings, Global}
import tools.nsc.plugins.PluginComponent
import tools.nsc.transform.{Transform, TypingTransformers}
import scala.tools.nsc.typechecker._
import tools.nsc.symtab.Flags._
import tools.nsc.ast.TreeDSL
import tools.nsc.interpreter._
import reflect.internal.{Flags, SymbolTable}
import reflect.ClassTag
import tools.scalap.scalax.rules.scalasig.MethodSymbol
//import tools.nsc.typechecker.bContexts.Context


class Transformer(val plugin: SinjectPlugin)
    extends PluginComponent
    with Transform
    with TypingTransformers
    with TreeDSL{

  val global = plugin.global
  import global._
  println("Starting Transformer")

  val runsAfter = List("typer")

  val phaseName = "sinjectTransformer"

  def newTransformer(unit: CompilationUnit) = new TypingTransformer(unit) {

    override def transform(tree: Tree): Tree = handleClass.orElse[Tree, Tree]{

      case a @ Apply(fun @ Select(qualifier, name), List(arg))
          if a.toString.contains("apply$default$1")
          && arg.symbol.owner.tpe.toString == "sinject.Module[T]" =>

        val newArgTrees = getArgTreesMatching("sinjected$" + a.tpe.toString().replace('.', '$'))

        val x = treeCopy.Apply(a, fun, newArgTrees)
        super.transform(x)

      case a @ Apply(fun, args)
        if a.symbol.tpe.toString.contains("sinjected$")
        && !a.toString.contains("sinjected$") =>

        val newArgTrees = getArgTreesMatching("sinjected$")

        val newA = treeCopy.Apply(a, fun, args ++ newArgTrees)

        newA.fun.tpe = newA.symbol.tpe

        super.transform(newA)

      case x =>super.transform(x)
    }.apply(tree)

    def getArgTreesMatching(s: String) = {
      val newArgSymbols =
        localTyper.context
          .owner.owner
          .info.decls
          .filter(_.name.toString.contains(s))
          .toList

      newArgSymbols.map{ sym =>
        val thisTree = This(localTyper.context.owner.owner)
        val newTree = Select(thisTree, sym.name)

        newTree.symbol = sym
        localTyper typed newTree
      }
    }

    val handleClass: PartialFunction[Tree, Tree] = {
      case cd @ ClassDef(mods, className, tparams, impl) =>
        val enclosingModules = for{
          symbol <- (unit.body.symbol.info.prefixChain :+ unit.body.symbol.info).reverse
          decl <- symbol.decls
          TypeRef(tpe, sym, Seq(singleType)) <- decl.typeOfThis.parents
          if sym.typeOfThis.toString == "sinject.Module[T]"
        } yield {
          singleType
        }

        def makeValDefs(flags: Long, filterThis: Boolean) = for{
          enclosing <- enclosingModules
          if !filterThis || enclosing != cd.symbol.tpe
        } yield {
          val paramSym = cd.symbol.asInstanceOf[ClassSymbol].newValueParameter(
              "sinjected$" + enclosing.toString().replace('.', '$'),
              cd.pos.focus,
              if (enclosing == cd.symbol.tpe)
                flags & ~PARAMACCESSOR
              else
                flags
            )

          paramSym setInfo enclosing

          localTyper.typedValDef(
             ValDef(
               paramSym,
               if (enclosing != cd.symbol.tpe) EmptyTree
               else This(cd.symbol)
             )
          )
        }

        val newValDefs = makeValDefs(IMPLICIT | PARAMACCESSOR | PRIVATE | LOCAL, false)
        val newConstrDefs = makeValDefs(PARAMACCESSOR | PARAM, true)

        newValDefs.map(x => cd.symbol.info.decls.enter(x.symbol))

        super.transform(treeCopy.ClassDef(
          cd,
          mods,
          className,
          tparams,
          treeCopy.Template(
            impl,
            impl.parents,
            impl.self,
            newValDefs ++ constructorTransform(impl.body, newConstrDefs)
          )
        ))

    }

    def constructorTransform(body: List[Tree], newConstrDefs: List[ValDef]): List[Tree] = body map {
      case dd @ DefDef(modifiers, name, tparams, vparamss, tpt, rhs)
        if name.toString == "<init>" =>

        val (newvparamss, extend) = vparamss match {
          case first :+ last =>
            (first :+ (last ++ newConstrDefs), true)
          case _ => (vparamss :+ newConstrDefs, false)
        }

        def recurse(t: Type, extend: Boolean): Type = t match {
          case NullaryMethodType(resultType) => t
          case MethodType(params, resultType: MethodType) => MethodType(params, recurse(resultType, extend))
          case MethodType(params, resultType) =>
            if (extend) MethodType(params ++ newConstrDefs.map(_.symbol), resultType)
            else MethodType(params, MethodType(newConstrDefs.map(_.symbol), resultType))
        }

        val res = treeCopy.DefDef(dd, modifiers, name, tparams, newvparamss, tpt, rhs)

        res.symbol setInfo recurse(res.symbol.info, extend)

        res

      case x => super.transform(x)
    }

    def openInterpreter(bind: ((String, Any), String)*){
      val repl = new ILoop
      repl.settings = new Settings
      repl.in = SimpleReader()

      repl.settings.Yreplsync.value = true

      repl.createInterpreter()

      repl.bind("global", global)
      repl.interpret("import global._")
      repl.bind("att", this: Any)
      for(((name, value), tpe) <- bind){
        repl.bind(s"a$name", value: Any)
        repl.interpret(s"val $name = a$name.asInstanceOf[$tpe]")
      }
      repl.loop()
      repl.closeInterpreter()
    }
  }

/*openInterpreter(
          "dd" -> dd -> "DefDef",
          "encl" -> enclosingModules -> "List[Type]"
        )*/


}
