package acyclic.plugin

import acyclic.plugin.Compat._
import scala.collection.{mutable, SortedSet}

trait BasePluginPhase[CompilationUnit, Tree] { self: GraphAnalysis[Tree] =>
  protected val cycleReporter: Seq[(Value, SortedSet[Int])] => Unit
  protected def force: Boolean
  protected def fatal: Boolean

  def treeLine(tree: Tree): Int
  def treeSymbolString(tree: Tree): String

  def reportError(msg: String): Unit
  def reportWarning(msg: String): Unit
  def reportInform(msg: String): Unit
  def reportEcho(msg: String, tree: Tree): Unit

  def units: Seq[CompilationUnit]
  def unitTree(unit: CompilationUnit): Tree
  def unitPath(unit: CompilationUnit): String
  def unitPkgName(unit: CompilationUnit): List[String]
  def findPkgObjects(tree: Tree): List[Tree]
  def pkgObjectName(pkgObject: Tree): String
  def hasAcyclicImport(tree: Tree, selector: String): Boolean

  final def findAcyclics(): (Seq[Value.File], Seq[Value.File], Seq[Value.Pkg]) = {
    val acyclicNodePaths = for {
      unit <- units if hasAcyclicImport(unitTree(unit), "file")
    } yield {
      Value.File(unitPath(unit), unitPkgName(unit))
    }
    val skipNodePaths = for {
      unit <- units if hasAcyclicImport(unitTree(unit), "skipped")
    } yield {
      Value.File(unitPath(unit), unitPkgName(unit))
    }

    val acyclicPkgNames = for {
      unit <- units
      pkgObject <- findPkgObjects(unitTree(unit))
      if hasAcyclicImport(pkgObject, "pkg")
    } yield Value.Pkg(pkgObjectName(pkgObject).split('.').toList)
    (skipNodePaths, acyclicNodePaths, acyclicPkgNames)
  }

  final def runOnNodes(nodes: Seq[Node[Value.File, Tree]]): Unit = {
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
    val components = DepNode.stronglyConnectedComponents(linkedNodes).filter(_.size > 1)

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
        cycleInfo.map { case (a, b) => a -> b.map(treeLine).to(SortedSet) }
      )

      val msg = "Unwanted cyclic dependency"
      if (fatal) {
        reportError(msg)
      } else {
        reportWarning(msg)
      }

      for (Seq((value, locs), (nextValue, _)) <- (cycleInfo :+ cycleInfo.head).sliding(2)) {
        reportInform("")
        value match {
          case Value.Pkg(pkg) => reportInform(s"package ${pkg.mkString(".")}")
          case Value.File(_, _) =>
        }

        reportEcho("", locs.head)

        val otherLines = locs.tail
          .map(treeLine)
          .filter(_ != treeLine(locs.head))

        reportInform("symbol: " + treeSymbolString(locs.head))

        if (!otherLines.isEmpty) {
          reportInform("More dependencies at lines " + otherLines.mkString(" "))
        }

      }
      reportInform("")
      usedNodes ++= cycle
    }
  }
}
