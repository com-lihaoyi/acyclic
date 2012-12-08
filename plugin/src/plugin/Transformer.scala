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


  val runsAfter = List("typer")

  val phaseName = "sinject"

  val moduleClass = definitions.getClass(newTypeName("sinject.Module"))

  val prefix = "sinj$"
  def typeToString(tpe: Type) = prefix + tpe.toString.split('.').map(_ charAt 0).mkString

  def newTransformer(unit: CompilationUnit) = new TypingTransformer(unit) {
    def getEnclosingModules(sym: ClassSymbol) = for{
      symbol <- sym.ownerChain.map(_.tpe).drop(1)
      decl <- symbol.decls
      TypeRef(tpe, sym, Seq(singleType)) <- decl.typeOfThis.parents
      //          if sym == moduleClass
      if decl.isModule
    } yield {
      singleType
    }
    override def transform(tree: Tree): Tree = tree match {
      /* add injected class members and constructor parameters */
      case cd @ ClassDef(mods, className, tparams, impl) =>
        println("Transforming Class " + cd)
        val enclosingModules = getEnclosingModules(cd.symbol.asClass)

        def makeValDefs(flags: Long, filterThis: Boolean) = for {
          enclosing <- enclosingModules
          if !filterThis || enclosing != cd.symbol.tpe
        } yield {
          val paramSym = cd.symbol.asInstanceOf[ClassSymbol].newValueParameter(
            typeToString(enclosing),
            cd.pos.focus,
            if (enclosing == cd.symbol.tpe) flags & ~PARAMACCESSOR
            else flags
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

      /* Transform calls to Module.apply() to inject the parameter */
      case a @ Apply(fun @ Select(qualifier, name), List(singleArg))
          if name == newTermName("apply")
          && qualifier.symbol.tpe.firstParent.typeSymbol == moduleClass =>

        val newArgTrees = getArgTreesMatching(_.name.toString == typeToString(a.tpe))

        val x = treeCopy.Apply(a, fun, newArgTrees)
        super.transform(x)

      /* Transform constructor calls to inject the parameter */
      case a @ Apply(fun, args)
        //if getEnclosingModules(a.tpe.selfsym.asClass) != List()
        if a.symbol.owner.isClass
        && fun.symbol.tpe.paramss.flatten.exists(_.name.toString.contains(prefix))
        && getEnclosingModules(a.symbol.owner.asClass).length > 0
        && fun.tpe.resultType == fun.tpe.finalResultType =>
        //a.symbol.o
//        openRepl("a" -> a -> "Apply",
//          "e" -> getEnclosingModules(a.symbol.owner.asClass) -> "List[Type]")
        val newArgsNeeded = fun.symbol.tpe.paramss.flatten.filter(_.name.toString.contains(prefix))
        val enclosingVersion = getEnclosingModules(a.symbol.owner.asClass)

        val newNames = enclosingVersion.map(x => typeToString(x))

        val newArgTrees = getArgTreesMatching(x => newNames.exists(x.name.toString == _))

        val newA = treeCopy.Apply(a, fun, args ++ newArgTrees)

        println(newA.fun.tpe + "------------>" + newA.symbol.tpe + "\t" + newA.fun.symbol.tpe)

        newA.fun.tpe = recurse(newA.fun.tpe, newArgTrees.map(_.symbol), true)


        super.transform(newA)

      case a @ Apply(fun, args)
        if fun.symbol.tpe.paramss.flatten.exists(_.name.toString.contains(prefix))
        && fun.tpe != fun.symbol.tpe =>
        fun.tpe = fun.symbol.tpe
        a
      case x =>
        println("Skipping " + x.shortClass)
        super.transform(x)

    }

    /**
     * Find all declarations in the parent scope which match some predicate
     * and construct argument Trees
     */
    def getArgTreesMatching(pred: Symbol => Boolean) = {
      val newArgSymbols =
        List(
          localTyper.context.owner.owner/*,
          localTyper.context.owner.owner.owner*/
        ).flatMap(
          _.info.decls
           .filter(pred)
        )

      def recurse: Symbol => List[Symbol] = {
        case NoSymbol => Nil
        case x: TermSymbol => recurse(x.owner)
        case x =>
          println(x)
          x.paramss.flatten ++ recurse(x.owner)
      }
      println("XYZ")

      println(recurse(localTyper.context.owner))


      newArgSymbols.map{ sym =>
        val thisTree = This(localTyper.context.owner.owner)
        val newTree = Select(thisTree, sym.name)

        newTree.symbol = sym
        localTyper typed newTree
      }
    }



    def constructorTransform(body: List[Tree], newConstrDefs: List[ValDef]): List[Tree] = body map {
      case dd @ DefDef(modifiers, name, tparams, vparamss, tpt, rhs)
        if name == newTermName("<init>") =>
        val (newvparamss, extend) = vparamss match {
          case first :+ last =>
            (first :+ (last ++ newConstrDefs), true)
          case _ => (vparamss :+ newConstrDefs, false)
        }

        val res = treeCopy.DefDef(dd, modifiers, name, tparams, newvparamss, tpt, rhs)

        res.symbol setInfo recurse(res.symbol.info,newConstrDefs.map(_.symbol), extend)
        res

      case x => x
    }
    def recurse(t: Type, newConstrSyms: List[Symbol], extend: Boolean): Type = t match {
      case NullaryMethodType(resultType) => t
      case MethodType(params, resultType: MethodType) => MethodType(params, recurse(resultType, newConstrSyms, extend))
      case MethodType(params, resultType) =>
        if (extend) MethodType(params ++ newConstrSyms, resultType)
        else MethodType(params, MethodType(newConstrSyms, resultType))
    }
    def openRepl(bind: ((String, Any), String)*){
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

/*openRepl(
          "dd" -> dd -> "DefDef",
          "encl" -> enclosingModules -> "List[Type]"
        )*/


}
