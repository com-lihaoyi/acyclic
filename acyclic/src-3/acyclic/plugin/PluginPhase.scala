package acyclic.plugin

import acyclic.file
import scala.collection.SortedSet
import dotty.tools.dotc.ast.tpd
import dotty.tools.dotc.{CompilationUnit, report}
import dotty.tools.dotc.core.Contexts.Context
import dotty.tools.dotc.core.Symbols.{NoSymbol, Symbol}
import dotty.tools.dotc.util.NoSource

/**
 * - Break dependency graph into strongly connected components
 * - Turn acyclic packages into virtual "files" in the dependency graph, as
 *   aggregates of all the files within them
 * - Any strongly connected component which includes an acyclic.file or
 *   acyclic.pkg is a failure
 *   - Pick an arbitrary cycle and report it
 * - Don't report more than one cycle per file/pkg, to avoid excessive spam
 */
class PluginPhase(
  protected val cycleReporter: Seq[(Value, SortedSet[Int])] => Unit,
  protected val force: Boolean,
  protected val fatal: Boolean
)(using ctx: Context) extends BasePluginPhase[CompilationUnit, tpd.Tree, Symbol], GraphAnalysis[tpd.Tree] {

  def treeLine(tree: tpd.Tree): Int = tree.sourcePos.line + 1
  def treeSymbolString(tree: tpd.Tree): String = tree.symbol.toString

  def reportError(msg: String): Unit = report.error(msg)
  def reportWarning(msg: String): Unit = report.warning(msg)
  def reportInform(msg: String): Unit = report.inform(msg)
  def reportEcho(msg: String, tree: tpd.Tree): Unit = report.echo(msg, tree.srcPos)

  private val pkgNameAccumulator = new tpd.TreeAccumulator[List[String]] {
    @annotation.tailrec
    private def definitivePackageDef(pkg: tpd.PackageDef): tpd.PackageDef =
      pkg.stats.collectFirst { case p: tpd.PackageDef => p } match {
        case Some(p) => definitivePackageDef(p)
        case None => pkg
      }

    def apply(acc: List[String], tree: tpd.Tree)(using Context) = tree match {
      case p: tpd.PackageDef => definitivePackageDef(p).pid.show :: acc
      case _ => foldOver(acc, tree)
    }
  }

  private val pkgObjectAccumulator = new tpd.TreeAccumulator[List[tpd.Tree]] {
    def apply(acc: List[tpd.Tree], tree: tpd.Tree)(using Context): List[tpd.Tree] =
      foldOver(
        if (tree.symbol.isPackageObject) tree :: acc else acc,
        tree
      )
  }

  private def hasAcyclicImportAccumulator(selector: String) = new tpd.TreeAccumulator[Boolean] {
    def apply(acc: Boolean, tree: tpd.Tree)(using Context): Boolean = tree match {
      case tpd.Import(expr, List(sel)) =>
        acc || (expr.symbol.toString == "object acyclic" && sel.name.show == selector)
      case _ => foldOver(acc, tree)
    }
  }

  lazy val units = Option(ctx.run) match {
    case Some(run) => run.units.toSeq.sortBy(_.source.content.mkString.hashCode())
    case None => Seq()
  }

  def unitTree(unit: CompilationUnit): tpd.Tree = unit.tpdTree
  def unitPath(unit: CompilationUnit): String = unit.source.path
  def unitPkgName(unit: CompilationUnit): List[String] = pkgNameAccumulator(Nil, unit.tpdTree).reverse.flatMap(_.split('.'))
  def findPkgObjects(tree: tpd.Tree): List[tpd.Tree] = pkgObjectAccumulator(Nil, tree).reverse
  def pkgObjectName(pkgObject: tpd.Tree): String = pkgObject.symbol.enclosingPackageClass.fullName.toString
  def hasAcyclicImport(tree: tpd.Tree, selector: String): Boolean = hasAcyclicImportAccumulator(selector)(false, tree)

  def extractDependencies(unit: CompilationUnit): Seq[(Symbol, tpd.Tree)] = DependencyExtraction(unit)
  def symbolPath(sym: Symbol): String = sym.source.path
  def isValidSymbol(sym: Symbol): Boolean = sym != NoSymbol && sym.source != null && sym.source != NoSource

  def run(): Unit = runAllUnits()
}
