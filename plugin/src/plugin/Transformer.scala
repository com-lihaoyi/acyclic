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
      case i @ Import(body, List(ImportSelector(name, _, _, _)))
        if name == newTermName("dynamic") =>

        body match {
          case Select(qualifier, treename) => Select(qualifier, treename.toTypeName)
          case Ident(treename) => Ident(treename.toTypeName)
        }
    }

    def makeValDefs(flags: Long) = for {
      (inj, i) <- injections.zipWithIndex
    } yield ValDef(Modifiers(flags), newTermName(plugin.prefix + i), inj, EmptyTree)

    def makeDefDefs(flags: Long) = for {
      (inj, i) <- injections.zipWithIndex
    } yield DefDef(Modifiers(flags), newTermName(plugin.prefix + i), List(), List(), inj, EmptyTree)

    override def transform(tree: Tree): Tree =  tree match {

      /* add injected class members and constructor parameters */
      case cd @ ClassDef(mods, className, tparams, impl)
        if !mods.hasFlag(TRAIT) =>

        val selfInject = unit.body.exists{
          case m @ ModuleDef(mods, termName, Template(parents, self, body))
            if parents.exists(_.toString().contains("sinject.Module"))
            && termName.toString == className.toString => true
          case _ => false
        }

        val newValDefs = makeValDefs(IMPLICIT | PARAMACCESSOR | PROTECTED | LOCAL)

        val newConstrDefs = makeValDefs(IMPLICIT | PARAMACCESSOR | PARAM)

        val transformedBody = constructorTransform(impl.body, newConstrDefs)

        // splitting to insert the newly defined implicit val into the
        // correct position in the list of arguments
        val (first, last) = transformedBody.splitAt(impl.body.indexWhere{
          case DefDef(mods, name, tparams, vparamss, tpt, rhs) => name == newTermName("<init>")
          case _ => false
        })


        val newThisDef =
          if (selfInject) Seq(thisTree(className))
          else Seq()

        val newAnnotation =
          if(selfInject) Seq(makeAnnotation("This requires an implicit " + className + " in scope."))
          else Seq()

        val newBody = newThisDef.toList ++
                      first ++
                      newValDefs ++
                      last

        cd.copy(mods = mods.copy(annotations = mods.annotations ++ newAnnotation), impl = impl.copy(body = newBody))

      case cd @ ClassDef(mods, className, tparams, impl)
        if mods.hasFlag(TRAIT) =>
        val newDefDefs = makeDefDefs(DEFERRED | IMPLICIT | PROTECTED | LOCAL)

        cd.copy(impl = impl.copy(body = newDefDefs ++ impl.body))

      case x => super.transform {x}
    }

    def constructorTransform(body: List[Tree], newConstrDefs: List[ValDef]): List[Tree] = body map {
      case dd @ DefDef(modifiers, name, tparams, vparamss, tpt, rhs)
        if name == newTermName("<init>") && newConstrDefs.length > 0 =>

        val newvparamss = vparamss match {
          case first :+ last if !last.isEmpty && last.forall(_.mods.hasFlag(IMPLICIT)) =>
            first :+ (last ++ newConstrDefs)
          case _ => vparamss :+ newConstrDefs
        }

        dd.copy(vparamss = newvparamss)

      case x => x
    }
    def makeAnnotation(msg: String) =
      Apply(
        Select(
          New(
            Select(
              Ident(
                newTermName("annotation")
              ),
              newTypeName("implicitNotFound")
            )
          ),
          newTermName("<init>")
        ),
        List(
          Literal(
            Constant(msg)
          )
        )
      )

    def thisTree(className: TypeName) =
      ValDef (
        Modifiers(IMPLICIT | PRIVATE | LOCAL),
        newTermName(plugin.prefix + "this"),
        Ident(className),
        This(className)
      )
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
