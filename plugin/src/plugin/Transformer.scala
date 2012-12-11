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
    } yield ValDef(Modifiers(flags), newTermName(plugin.prefix + i), inj, EmptyTree)

    def makeDefDefs(flags: Long) = for {
      (inj, i)<- injections.zipWithIndex
    } yield DefDef(Modifiers(flags), newTermName(plugin.prefix + i), List(), List(), inj, EmptyTree)

    override def transform(tree: Tree): Tree =  tree match {

      /* add injected class members and constructor parameters */
      case cd @ ClassDef(mods, className, tparams, impl)
        if !mods.hasFlag(TRAIT) =>

        val selfInject = unit.body.exists{
          case m @ ModuleDef(mods, termName, Template(parents, self, body))
            if parents.exists(_.toString().contains("sinject.Module")) => true
          case _ => false
        }
        val newValDefs = makeValDefs(IMPLICIT | PARAMACCESSOR | PROTECTED | LOCAL)
        val newConstrDefs = makeValDefs(IMPLICIT | PARAMACCESSOR | PARAM)
        val (first, last) = constructorTransform(impl.body, newConstrDefs).splitAt(impl.body.indexWhere{
          case DefDef(mods, name, tparams, vparamss, tpt, rhs) => name == newTermName("<init>")
          case _ => false
        })


        val newThisDef =
          if (selfInject)
            Seq(ValDef (
              Modifiers(IMPLICIT),
              newTermName(plugin.prefix + "thisDef"),
              Ident(className),
              This(className)
            ))
          else
            Seq()

        val newBody = newThisDef.toList ++
                      first ++
                      newValDefs ++
                      last

        cd.copy(impl = impl.copy(body = newBody))

      case cd @ ClassDef(mods, className, tparams, impl)
        if mods.hasFlag(TRAIT) =>
        val newDefDefs = makeDefDefs(DEFERRED | IMPLICIT | PROTECTED | LOCAL)

        cd.copy(impl = impl.copy(body = newDefDefs ++ impl.body))

      case x => super.transform {x}
    }

    def constructorTransform(body: List[Tree], newConstrDefs: List[ValDef]): List[Tree] = body map {
      case dd @ DefDef(modifiers, name, tparams, vparamss, tpt, rhs)
        if name == newTermName("<init>") && newConstrDefs.length > 0 =>

        val (newvparamss, extend) = vparamss match {
          case first :+ last if !last.isEmpty && last.forall(_.mods.hasFlag(IMPLICIT)) =>
            (first :+ (last ++ newConstrDefs), true)
          case _ => (vparamss :+ newConstrDefs, false)
        }

        dd.copy(vparamss = newvparamss)

      case x => x
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
