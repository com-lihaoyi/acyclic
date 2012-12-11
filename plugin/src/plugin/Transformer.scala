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

  val prefix = "sinj$"

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

    def makeValDefs(flags: Long) = for {
      (inj, i)<- injections.zipWithIndex
    } yield ValDef(Modifiers(flags), newTermName(prefix + i), inj, EmptyTree)


    def newConstrDefs = makeValDefs(IMPLICIT | PARAMACCESSOR | PARAM)
    def newValDefs = makeValDefs(IMPLICIT | PARAMACCESSOR | PRIVATE | LOCAL)
    override def transform(tree: Tree): Tree =  tree match {
      case cd @ ClassDef(mods, className, tparams, impl)
        if !mods.hasFlag(MODULE) =>

        val selfInject = unit.body.collect{
          case ModuleDef(mods, termName, Template(parents, self, body))
            if parents.exists(_.toString().contains("sinject.Module")) => true
        }.headOption.isDefined

        val newThisDef = if (selfInject) Some(ValDef (
          Modifiers(IMPLICIT),
          newTermName("thisDef"),
          Ident(className),
          This(className)
        )) else None

        val newBody = impl.body map {
          case dd @ DefDef(modifiers, name, tparams, vparamss, tpt, rhs) =>
            val newvparamss = vparamss match {
              case first :+ last if !last.isEmpty && last.forall(_.mods.hasFlag(IMPLICIT)) =>
                first :+ (last ++ newConstrDefs)
              case all if newConstrDefs.length > 0 => all :+ newConstrDefs
              case _ => vparamss
            }
            treeCopy.DefDef(dd, modifiers, name, tparams, newvparamss, tpt, rhs)
          case x => x
        }
        super.transform(treeCopy.ClassDef(
          cd,
          mods,
          className,
          tparams,
          treeCopy.Template(
            impl,
            impl.parents,
            impl.self,
            newThisDef.toList ++ newValDefs ++ newBody
          )
        ))




      case x => super.transform {x}
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
