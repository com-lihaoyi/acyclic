package acyclic.plugin

import scala.tools.nsc.Phase
import tools.nsc.plugins.PluginComponent

/**
 * Injects implicit parameters into the top-level method and class signatures
 * throughout every compilation unit, based upon what imports it finds with
 * the name `dynamic` within the source file
 */
class Transformer(val plugin: Plugin) extends PluginComponent {

  val global = plugin.global
  import global._

  val runsAfter = List("typer")
  override val runsRightAfter = Some("typer")

  val phaseName = "sinject"

  override def newPhase(prev: Phase): StdPhase = new StdPhase(prev) {
    override def apply(unit: CompilationUnit) {
      println("-----------------------------------")
      println(unit.source.path)
      val syms = Dependency.byMembers(unit) | Dependency.byInheritence(unit)

      syms.map(_.sourceFile)
          .filter(_ != null)
          .map("\t" + _.path)
          .foreach(println)
    }
  }
  object Dependency{
    private final class CollectTypeTraverser[T](pf: PartialFunction[Type, T]) extends TypeTraverser {
      var collected: List[T] = Nil
      def traverse(tpe: Type): Unit = {
        if (pf.isDefinedAt(tpe))
          collected = pf(tpe) :: collected
        mapOver(tpe)
      }
    }

    private abstract class ExtractDependenciesTraverser extends Traverser {
      protected val depBuf = collection.mutable.ArrayBuffer.empty[Symbol]
      protected def addDependency(dep: Symbol): Unit = depBuf += dep
      def dependencies: collection.immutable.Set[Symbol] = {
        // convert to immutable set and remove NoSymbol if we have one
        depBuf.toSet - NoSymbol
      }
    }

    private class ExtractDependenciesByMemberRefTraverser extends ExtractDependenciesTraverser {
      override def traverse(tree: Tree): Unit = {
        tree match {
          case Import(expr, selectors) =>
            selectors.foreach {
              case ImportSelector(nme.WILDCARD, _, null, _) =>
              // in case of wildcard import we do not rely on any particular name being defined
              // on `expr`; all symbols that are being used will get caught through selections
              case ImportSelector(name: Name, _, _, _) =>
                def lookupImported(name: Name) = expr.symbol.info.member(name)
                // importing a name means importing both a term and a type (if they exist)
                addDependency(lookupImported(name.toTermName))
                addDependency(lookupImported(name.toTypeName))
            }
          case select: Select =>
            addDependency(select.symbol)
          /*
           * Idents are used in number of situations:
           *  - to refer to local variable
           *  - to refer to a top-level package (other packages are nested selections)
           *  - to refer to a term defined in the same package as an enclosing class;
           *    this looks fishy, see this thread:
           *    https://groups.google.com/d/topic/scala-internals/Ms9WUAtokLo/discussion
           */
          case ident: Ident =>
            addDependency(ident.symbol)
          case typeTree: TypeTree =>
            val typeSymbolCollector = new CollectTypeTraverser({
              case tpe if !tpe.typeSymbol.isPackage => tpe.typeSymbol
            })
            typeSymbolCollector.traverse(typeTree.tpe)
            val deps = typeSymbolCollector.collected.toSet
            deps.foreach(addDependency)
          case Template(parents, self, body) =>
            traverseTrees(body)
          case other => ()
        }
        super.traverse(tree)
      }
    }

    def byMembers(unit: CompilationUnit): collection.immutable.Set[Symbol] = {
      val traverser = new ExtractDependenciesByMemberRefTraverser
      traverser.traverse(unit.body)
      val dependencies = traverser.dependencies
      dependencies.map(enclosingTopLevelClass)
    }

    /** Copied straight from Scala 2.10 as it does not exist in Scala 2.9 compiler */
    private final def debuglog(msg: => String) {
      if (settings.debug.value)
        log(msg)
    }

    private final class ExtractDependenciesByInheritanceTraverser extends ExtractDependenciesTraverser {
      override def traverse(tree: Tree): Unit = tree match {
        case Template(parents, self, body) =>
          // we are using typeSymbol and not typeSymbolDirect because we want
          // type aliases to be expanded
          val parentTypeSymbols = parents.map(parent => parent.tpe.typeSymbol).toSet
          debuglog("Parent type symbols for " + tree.pos + ": " + parentTypeSymbols.map(_.fullName))
          parentTypeSymbols.foreach(addDependency)
          traverseTrees(body)
        case tree => super.traverse(tree)
      }
    }

    def byInheritence(unit: CompilationUnit): collection.immutable.Set[Symbol] = {
      val traverser = new ExtractDependenciesByInheritanceTraverser
      traverser.traverse(unit.body)
      val dependencies = traverser.dependencies
      dependencies.map(enclosingTopLevelClass)
    }

    /**
     * We capture enclosing classes only because that's what CompilationUnit.depends does and we don't want
     * to deviate from old behaviour too much for now.
     */
    private def enclosingTopLevelClass(sym: Symbol): Symbol =
    // for Scala 2.8 and 2.9 this method is provided through SymbolCompat
      sym.enclosingTopLevelClass
  }
}
