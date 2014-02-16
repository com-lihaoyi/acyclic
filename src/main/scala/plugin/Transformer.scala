package plugin

import tools.nsc.{Settings, Global}
import tools.nsc.plugins.PluginComponent
import tools.nsc.transform.{Transform, TypingTransformers}

import tools.nsc.symtab.Flags._
import tools.nsc.ast.TreeDSL
import tools.nsc.interpreter._
import com.sun.xml.internal.ws.api.model.wsdl.WSDLBoundOperation.ANONYMOUS

/**
 * Injects implicit parameters into the top-level method and class signatures
 * throughout every compilation unit, based upon what imports it finds with
 * the name `dynamic` within the source file
 */
class Transformer(val plugin: SinjectPlugin)
extends PluginComponent
with Transform
with TypingTransformers{

  val global = plugin.global
  import global._

  val runsAfter = List("parser")
  override val runsRightAfter = Some("parser")

  val phaseName = "sinject"

  def newTransformer(unit: CompilationUnit) = {
    val a = extractDependenciesByInheritance(unit)
    val b = extractDependenciesByMemberRef(unit)
    println()
    println(unit.source)
    println(a)
    println(b)
    println(a.map(_.sourceFile))
    println(b.map(_.sourceFile))
    new TypingTransformer(unit){}
  }

  /**
   * Traverses given type and collects result of applying a partial function `pf`.
   *
   * NOTE: This class exists in Scala 2.10 as CollectTypeCollector but does not in earlier
   * versions (like 2.9) of Scala compiler that incremental cmpiler supports so we had to
   * reimplement that class here.
   */
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
            case tpe if tpe != null && !tpe.typeSymbol.isPackage => tpe.typeSymbol
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

  def extractDependenciesByMemberRef(unit: CompilationUnit): collection.immutable.Set[Symbol] = {
    val traverser = new ExtractDependenciesByMemberRefTraverser
    traverser.traverse(unit.body)
    val dependencies = traverser.dependencies
    dependencies.map(enclosingTopLevelClass)
  }



  private final class ExtractDependenciesByInheritanceTraverser extends ExtractDependenciesTraverser {
    override def traverse(tree: Tree): Unit = tree match {
      case Template(parents, self, body) =>
        // we are using typeSymbol and not typeSymbolDirect because we want
        // type aliases to be expanded
        val parentTypeSymbols = parents.filter(_.tpe != null).map(_.tpe.typeSymbol).toSet
        debuglog("Parent type symbols for " + tree.pos + ": " + parentTypeSymbols.map(_.fullName))
        parentTypeSymbols.foreach(addDependency)
        traverseTrees(body)
      case tree => super.traverse(tree)
    }
  }

  def extractDependenciesByInheritance(unit: CompilationUnit): collection.immutable.Set[Symbol] = {
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