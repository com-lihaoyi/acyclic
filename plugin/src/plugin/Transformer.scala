package plugin

import tools.nsc.{Settings, Global}
import tools.nsc.plugins.PluginComponent
import tools.nsc.transform.{Transform, TypingTransformers}

import tools.nsc.symtab.Flags._
import tools.nsc.ast.TreeDSL
import tools.nsc.interpreter._



class Transformer(val plugin: SinjectPlugin)
    extends PluginComponent
    with Transform
    with TypingTransformers
    with TreeDSL{

  val global = plugin.global
  import global._

  val runsAfter = List("parser")
  override val runsRightAfter = Some("parser")
  val phaseName = "sinject"

  val moduleClass = rootMirror getClassByName newTypeName("sinject.Module")

  val prefix = "sinj$"

  def typeToString(tpe: Type) = newTermName(prefix + tpe.toString.split('.').map(_ charAt 0).mkString)

  def newTransformer(unit: CompilationUnit) = new TypingTransformer(unit) {
    val injections = unit.body.collect{
      case i @ Import(
        Select(qualifier, treename),
        List(ImportSelector(name, namepos, rename, renamepos)))
        if name == newTermName("dynamic") =>

        Select(qualifier, treename.toTypeName)
      case i @ Import(
        Ident(treename),
        List(ImportSelector(name, namepos, rename, renamepos)))
        if name == newTermName("dynamic")=>

        Ident(treename.toTypeName)
    }

    override def transform(tree: Tree): Tree = super.transform { tree match {
      /* add injected class members and constructor parameters */
      case cd @ ClassDef(mods, className, tparams, impl) =>
        val selfInject = unit.body.collect{
          case ModuleDef(mods, termName, Template(parents, self, body))
            if parents.exists(_.toString().contains("sinject.Module")) => true
        }.headOption.isDefined

        def makeValDefs(flags: Long) = for {
          (inj, i)<- injections.zipWithIndex
        } yield ValDef(Modifiers(flags), newTermName(prefix + i), inj, EmptyTree)


        val newValDefs = makeValDefs(IMPLICIT | PARAMACCESSOR | PRIVATE | LOCAL)
        val newConstrDefs = makeValDefs(IMPLICIT | PARAMACCESSOR | PARAM)

        val newThisDef = if (selfInject) Some(ValDef (
          Modifiers(IMPLICIT),
          newTermName("thisDef"),
          Ident(className),
          This(className)
        )) else None

        treeCopy.ClassDef(
          cd,
          mods,
          className,
          tparams,
          treeCopy.Template(
            impl,
            impl.parents,
            impl.self,
            newThisDef.toList ++ newValDefs ++ constructorTransform(impl.body, newConstrDefs)
          )
        )
      case x => x
    }}



    def constructorTransform(body: List[Tree], newConstrDefs: List[ValDef]): List[Tree] = body map {
      case dd @ DefDef(modifiers, name, tparams, vparamss, tpt, rhs)
        if name == newTermName("<init>")
        && newConstrDefs.length > 0 =>

        println("constr")
        val (newvparamss, extend) = vparamss match {
          case first :+ last if !last.isEmpty && last.forall(_.mods.hasFlag(IMPLICIT)) =>
            (first :+ (last ++ newConstrDefs), true)
          case _ => (vparamss :+ newConstrDefs, false)
        }

        val res = treeCopy.DefDef(dd, modifiers, name, tparams, newvparamss, tpt, rhs)

        res

      case x =>
        println("skip " + x + " : " + newConstrDefs.length)
        x
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
}
