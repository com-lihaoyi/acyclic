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


class Transformer(val plugin: SinjectPlugin)
    extends PluginComponent
    with Transform
    with TypingTransformers
    with TreeDSL{

  val global = plugin.global
  import global._
  println("Starting Transformer")

  val runsAfter = List("typer")
  //override val runsRightAfter = Some("typer")

  val phaseName = "sinjectTransformer"

  def newTransformer(unit: CompilationUnit) = new TypingTransformer(unit) {

    override def transform(tree: Tree): Tree = tree match{
      case cd @ ClassDef(mods, className, tparams, impl) =>
        println("\n###ClassDef " + className)
        println(global.analyzer)
        val enclosingModules = for{
          symbol <- (unit.body.symbol.info.prefixChain :+ unit.body.symbol.info).reverse
          decl <- symbol.decls
          TypeRef(tpe, sym, Seq(singleType)) <- decl.typeOfThis.parents
          if sym.typeOfThis.toString == "sinject.Module[T]"
          if singleType != cd.symbol.tpe
        } yield {
          singleType
        }

        if (enclosingModules.length == 0) super.transform(cd)
        else {
          def makeValDefs(flags: Long) = for{
            enclosing <- enclosingModules
          } yield {
            val paramSym =
              cd.symbol.asInstanceOf[ClassSymbol]
                .newValueParameter(
                "sinjected$" + enclosing.toString().replace('.', '$'),
                cd.pos.focus,
                flags
              )

            paramSym.setInfo(enclosing)

            localTyper.typedValDef(ValDef(paramSym))
          }

          val newValDefs = makeValDefs(IMPLICIT | PARAMACCESSOR | PRIVATE)
          val newConstrDefs = makeValDefs(IMPLICIT | PARAMACCESSOR | PARAM)

          newValDefs.map(x => cd.symbol.info.decls.enter(x.symbol))

          val newTree = treeCopy.ClassDef(
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
          )

          super.transform(newTree)
        }

      case a @ Apply(fun, args) if fun.tpe != fun.symbol.tpe =>
        //openInterpreter("a" -> a -> "Apply")
        println("=================================")
        println(a)
        println("A")
        println(fun.tpe)
        println("B")
        println(fun.symbol.tpe)
        println("C")
        a.fun.tpe = a.fun.symbol.tpe
        a.tpe = a.fun.symbol.tpe.resultType

        val x = localTyper.applyImplicitArgs{ a }

        println(x)
        super.transform(x)
      case x =>super.transform(x)
    }


    def constructorTransform(body: List[Tree], newConstrDefs: List[ValDef]): List[Tree] = body map {
      case dd @ DefDef(modifiers, name, tparams, vparamss, tpt, rhs)
        if name.toString == "<init>" =>

        val (newvparamss, extend) = vparamss match {
          case first :+ last
            if last.forall(_.mods.hasFlag(Flags.IMPLICIT))
            && last.length > 0 =>
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

        res.symbol.setInfo(recurse(res.symbol.info, extend))

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
