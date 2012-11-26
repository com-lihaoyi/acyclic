import tools.nsc.doc.Settings
import tools.nsc.interpreter.{SimpleReader, NamedParam, ILoop}
import tools.nsc.plugins.{Plugin, PluginComponent}
import tools.nsc.Global

import tools.nsc.transform.{TypingTransformers, InfoTransform, Transform}
import tools.nsc.symtab.Flags

class MyPlugin(val global: Global) extends Plugin { plugin =>
  val name = "sinject"
  val description = "Injects implicit parameters throughout all classes within a package"
  val components = List[PluginComponent](AnnotationsInjectComponent)

  private object AnnotationsInjectComponent extends PluginComponent with Transform with TypingTransformers {
    val global = plugin.global
    import global._

    val runsAfter = List("parser")
    override val runsBefore = List("namer")
    val phaseName = plugin.name

    def newTransformer(unit: CompilationUnit) = new AnnotationsInjectTransformer(unit)

    val bindingModule = "bindingModule"
    val bindingModuleType = Ident(newTypeName("Prog"))

    class AnnotationsInjectTransformer(unit: CompilationUnit) extends TypingTransformer(unit) {

      override def transform (tree:Tree):Tree = {
        import Flags._

        def NewValDef(l: Long) = new ValDef(
          mods = Modifiers(l),
          name = bindingModule,
          tpt = bindingModuleType,
          rhs = EmptyTree
        )

        super.transform(tree match {
          case cd @ ClassDef(mods, name, _, classBody)
            if mods.annotations.find(_.children(0).children(0).children(0).toString() == "sinject.Module").isEmpty => {

            val body = classBody.body.map {
              case item @ DefDef(_, termname, _, vparams, _, _) if termname.toString == "<init>" =>
                item.copy(vparamss = vparams :+ List(NewValDef(IMPLICIT | PARAM | PARAMACCESSOR)))
              case t => t
            }

            cd.copy(impl = Template(classBody.parents, classBody.self, NewValDef(IMPLICIT | PARAMACCESSOR) :: body))
          }
          case p @ PackageDef(pid, stats) =>
            println("===package===")
            println(pid)
            println(stats)
            p
          case t => t
        })
      }
    }
  }
}