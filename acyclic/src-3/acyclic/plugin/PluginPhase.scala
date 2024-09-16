package acyclic.plugin

import acyclic.file
import scala.collection.{SortedSet, mutable}
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
  cycleReporter: Seq[(Value, SortedSet[Int])] => Unit,
  force: => Boolean,
  fatal: => Boolean
)(using ctx: Context) extends GraphAnalysis[tpd.Tree] { t =>

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

      Node[Value.File](
        Value.File(unit.source.path, pkgName(unit)),
        connections.groupBy(c => Value.File(c._1, pkgName(unitMap(c._1))): Value)
          .mapValues(_.map(_._2))
          .toMap
      )
    }

    val nodeMap = nodes.map(n => n.value -> n).toMap

    val (skipNodePaths, acyclicFiles, acyclicPkgs) = findAcyclics()

    val allAcyclics = acyclicFiles ++ acyclicPkgs

    // synthetic nodes for packages, which aggregate the dependencies of
    // their contents
    val pkgNodes = acyclicPkgs.map { value =>
      Node(
        value,
        nodes.filter(_.value.pkg.startsWith(value.pkg))
          .flatMap(_.dependencies.toSeq)
          .groupBy(_._1)
          .mapValues(_.flatMap(_._2))
          .toMap
      )
    }

    val linkedNodes: Seq[DepNode] = (nodes ++ pkgNodes).map { d =>
      val extraLinks = d.dependencies.flatMap {
        case (value: Value.File, pos) =>
          for {
            acyclicPkg <- acyclicPkgs
            if nodeMap(value).value.pkg.startsWith(acyclicPkg.pkg)
            if !d.value.pkg.startsWith(acyclicPkg.pkg)
          } yield (acyclicPkg, pos)

        case (_: Value.Pkg, _) => Nil
      }
      d.copy(dependencies = d.dependencies ++ extraLinks)
    }

    // only care about cycles with size > 1 here
    val components = DepNode.stronglyConnectedComponents(linkedNodes)
      .filter(_.size > 1)

    val usedNodes = mutable.Set.empty[DepNode]
    for {
      c <- components
      n <- c
      if !usedNodes.contains(n)
      if (!force && allAcyclics.contains(n.value)) || (force && !skipNodePaths.contains(n.value))
    } {
      val cycle = DepNode.smallestCycle(n, c)
      val cycleInfo =
        (cycle :+ cycle.head).sliding(2)
          .map { case Seq(a, b) => (a.value, a.dependencies(b.value)) }
          .toSeq
      cycleReporter(
        cycleInfo.map { case (a, b) => a -> b.map(_.sourcePos.line + 1).to(SortedSet) }
      )

      val msg = "Unwanted cyclic dependency"
      if (fatal) {
        report.error(msg)
      } else {
        report.warning(msg)
      }

      for (Seq((value, locs), (nextValue, _)) <- (cycleInfo :+ cycleInfo.head).sliding(2)) {
        report.inform("")
        value match {
          case Value.Pkg(pkg) => report.inform(s"package ${pkg.mkString(".")}")
          case Value.File(_, _) =>
        }

        report.echo("", locs.head.srcPos)

        val otherLines = locs.tail
          .map(_.sourcePos.line + 1)
          .filter(_ != locs.head.sourcePos.line + 1)

        report.inform("symbol: " + locs.head.symbol.toString)

        if (!otherLines.isEmpty) {
          report.inform("More dependencies at lines " + otherLines.mkString(" "))
        }

      }
      report.inform("")
      usedNodes ++= cycle
    }
  }

}
