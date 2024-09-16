package acyclic.plugin

import acyclic.file
import scala.collection.SortedSet
import dotty.tools.dotc.ast.tpd
import dotty.tools.dotc.{CompilationUnit, report}
import dotty.tools.dotc.core.Contexts.Context
import dotty.tools.dotc.core.Symbols.NoSymbol
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
)(using ctx: Context) extends BasePluginPhase[tpd.Tree], GraphAnalysis[tpd.Tree] {

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

  private def pkgName(unit: CompilationUnit) =
    pkgNameAccumulator(Nil, unit.tpdTree).reverse.flatMap(_.split('.'))

  private lazy val units = Option(ctx.run) match {
    case Some(run) => run.units.toSeq.sortBy(_.source.content.mkString.hashCode())
    case None => Seq()
  }

  private def hasImport(selector: String, tree: tpd.Tree): Boolean = {
    val accumulator = new tpd.TreeAccumulator[Boolean] {
      def apply(acc: Boolean, tree: tpd.Tree)(using Context): Boolean = tree match {
        case tpd.Import(expr, List(sel)) =>
          acc || (expr.symbol.toString == "object acyclic" && sel.name.show == selector)
        case _ => foldOver(acc, tree)
      }
    }

    accumulator(false, tree)
  }

  private val pkgObjectAccumulator = new tpd.TreeAccumulator[List[tpd.Tree]] {
    def apply(acc: List[tpd.Tree], tree: tpd.Tree)(using Context): List[tpd.Tree] =
      foldOver(
        if (tree.symbol.isPackageObject) tree :: acc else acc,
        tree
      )
  }

  def findAcyclics() = {
    val acyclicNodePaths = for {
      unit <- units if hasImport("file", unit.tpdTree)
    } yield {
      Value.File(unit.source.path, pkgName(unit))
    }
    val skipNodePaths = for {
      unit <- units if hasImport("skipped", unit.tpdTree)
    } yield {
      Value.File(unit.source.path, pkgName(unit))
    }

    val acyclicPkgNames = for {
      unit <- units
      pkgObject <- pkgObjectAccumulator(Nil, unit.tpdTree).reverse
      if hasImport("pkg", pkgObject)
    } yield {
      Value.Pkg(
        pkgObject.symbol
          .enclosingPackageClass
          .fullName
          .toString
          .split('.')
          .toList
      )
    }
    (skipNodePaths, acyclicNodePaths, acyclicPkgNames)
  }

  def run(): Unit = {
    val unitMap = units.map(u => u.source.path -> u).toMap
    val nodes = for (unit <- units) yield {

      val deps = DependencyExtraction(unit)

      val connections = for {
        (sym, tree) <- deps
        if sym != NoSymbol
        if sym.source != null
        if sym.source != NoSource
        if sym.source.path != unit.source.path
        if unitMap.contains(sym.source.path)
      } yield (sym.source.path, tree)

      Node[Value.File, tpd.Tree](
        Value.File(unit.source.path, pkgName(unit)),
        connections.groupBy(c => Value.File(c._1, pkgName(unitMap(c._1))): Value)
          .mapValues(_.map(_._2))
          .toMap
      )
    }

    runOnNodes(nodes)
  }
}
