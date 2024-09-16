package acyclic.plugin

import acyclic.file
import dotty.tools.dotc.ast.tpd
import dotty.tools.dotc.{CompilationUnit, report}
import dotty.tools.dotc.core.Contexts.Context
import dotty.tools.dotc.core.Flags
import dotty.tools.dotc.core.Names.Name
import dotty.tools.dotc.core.Symbols.Symbol
import dotty.tools.dotc.core.Types.Type

object DependencyExtraction {
  def apply(unit: CompilationUnit)(using Context): Seq[(Symbol, tpd.Tree)] = {

    class CollectTypeTraverser[T](pf: PartialFunction[Type, T]) extends tpd.TreeAccumulator[List[T]] {
      def apply(acc: List[T], tree: tpd.Tree)(using Context) =
        foldOver(
          if (pf.isDefinedAt(tree.tpe)) pf(tree.tpe) :: acc else acc,
          tree
        )
    }

    abstract class ExtractDependenciesTraverser extends tpd.TreeTraverser {
      protected val depBuf = collection.mutable.ArrayBuffer.empty[(Symbol, tpd.Tree)]
      protected def addDependency(sym: Symbol, tree: tpd.Tree): Unit = depBuf += ((sym, tree))
      def dependencies: collection.immutable.Set[(Symbol, tpd.Tree)] = {
        // convert to immutable set and remove NoSymbol if we have one
        depBuf.toSet
      }

    }

    class ExtractDependenciesByMemberRefTraverser extends ExtractDependenciesTraverser {
      override def traverse(tree: tpd.Tree)(using Context): Unit = {
        tree match {
          case i @ tpd.Import(expr, selectors) =>
            selectors.foreach { s =>
              def lookupImported(name: Name) = expr.symbol.info.member(name).symbol

              if (s.isWildcard) {
                addDependency(lookupImported(s.name.toTermName), tree)
                addDependency(lookupImported(s.name.toTypeName), tree)
              }
            }
          case select: tpd.Select =>
            addDependency(select.symbol, tree)
          /*
           * Idents are used in number of situations:
           *  - to refer to local variable
           *  - to refer to a top-level package (other packages are nested selections)
           *  - to refer to a term defined in the same package as an enclosing class;
           *    this looks fishy, see this thread:
           *    https://groups.google.com/d/topic/scala-internals/Ms9WUAtokLo/discussion
           */
          case ident: tpd.Ident =>
            addDependency(ident.symbol, tree)
          case typeTree: tpd.TypeTree =>
            val typeSymbolCollector = new CollectTypeTraverser({
              case tpe if tpe != null && tpe.typeSymbol != null && !tpe.typeSymbol.is(Flags.Package) => tpe.typeSymbol
            })
            val deps = typeSymbolCollector(Nil, typeTree).toSet
            deps.foreach(addDependency(_, tree))
          case t: tpd.Template =>
            traverse(t.body)
          case other => ()
        }
        foldOver((), tree)
      }
    }

    def byMembers(): collection.immutable.Set[(Symbol, tpd.Tree)] = {
      val traverser = new ExtractDependenciesByMemberRefTraverser
      if (!unit.isJava)
        traverser.traverse(unit.tpdTree)
      traverser.dependencies
    }

    class ExtractDependenciesByInheritanceTraverser extends ExtractDependenciesTraverser {
      override def traverse(tree: tpd.Tree)(using Context): Unit = tree match {
        case t: tpd.Template =>
          // we are using typeSymbol and not typeSymbolDirect because we want
          // type aliases to be expanded
          val parentTypeSymbols = t.parents.map(parent => parent.tpe.typeSymbol).toSet
          report.debuglog("Parent type symbols for " + tree.sourcePos.show + ": " + parentTypeSymbols.map(_.fullName))
          parentTypeSymbols.foreach(addDependency(_, tree))
          traverse(t.body)
        case tree => foldOver((), tree)
      }
    }

    def byInheritence(): collection.immutable.Set[(Symbol, tpd.Tree)] = {
      val traverser = new ExtractDependenciesByInheritanceTraverser
      if (!unit.isJava)
        traverser.traverse(unit.tpdTree)
      traverser.dependencies
    }

    (byMembers() | byInheritence()).toSeq
  }
}
