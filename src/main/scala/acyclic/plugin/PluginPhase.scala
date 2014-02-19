
package acyclic.plugin
import acyclic.file
import collection.mutable
import scala.tools.nsc.{Global, Phase}
import tools.nsc.plugins.PluginComponent



/**
 * - Break dependency graph into strongly connected components
 * - Any strongly connected component which includes in acyclic.file is a failure
 *   - Pick an arbitrary cycle and report it
 * - Any strongly connected component which is partially in a acyclic.pkg is a failure
 *   - Pick an arbitrary cycle and report it
 */
class PluginPhase(val global: Global, cycleReporter: Seq[(Value, Set[Int])] => Unit)
                  extends PluginComponent
                  with GraphAnalysis { t =>

  import global._

  val runsAfter = List("typer")

  override val runsRightAfter = Some("typer")

  val phaseName = "acyclic"
  def pkgName(unit: CompilationUnit) = {
    unit.body
        .collect{case x: PackageDef => x.pid.toString}
        .flatMap(_.split('.'))
  }

  def findAcyclics() = {
    val acyclicNodePaths = for {
      unit <- global.currentRun.units.toSet
      if unit.body.children.collect{
        case Import(expr, List(sel)) =>
          expr.symbol.toString == "package acyclic" && sel.name.toString == "file"
      }.exists(x => x)
    } yield {

      Value.File(unit.source.path, pkgName(unit))
    }

    val acyclicPkgNames = for {
      unit <- global.currentRun.units.toSeq
      pkgObject <- unit.body.collect{case x: ModuleDef if x.name.toString == "package" => x }
      if !pkgObject.impl.children.collect{case Import(expr, List(sel)) =>
        expr.symbol.toString == "package acyclic" && sel.name.toString == "pkg"
      }.isEmpty
    } yield Value.Pkg(
        pkgObject.symbol
                 .enclosingPackageClass
                 .fullName
                 .split('.')
                 .toList
      )
    (acyclicNodePaths, acyclicPkgNames)
  }

  override def newPhase(prev: Phase): Phase = new Phase(prev) {
    override def run() {
      val unitMap = global.currentRun.units.map(u => u.source.path -> u).toMap
      val nodes = for (unit <- global.currentRun.units.toList) yield {

        val deps = DependencyExtraction(t.global)(unit)

        val connections = for{
          (sym, tree) <- deps
          if sym != NoSymbol
          if sym.sourceFile != null
          if sym.sourceFile.path != unit.source.path
        } yield (sym.sourceFile.path, tree.pos)

        Node[Value.File](
          Value.File(unit.source.path, pkgName(unit)),
          connections.groupBy(c => Value.File(c._1, pkgName(unitMap(c._1))): Value)
                     .mapValues(_.map(_._2))
        )
      }

      val nodeMap = nodes.map(n => n.value -> n).toMap

      val (acyclicFiles, acyclicPkgs) = findAcyclics()

      // synthetic nodes for packages, which aggregate the dependencies of
      // their contents
      val pkgNodes = acyclicPkgs.map{ value =>
        Node(
          value,
          nodes.filter(_.value.pkg.startsWith(value.pkg))
               .flatMap(_.dependencies.toSeq)
               .groupBy(_._1)
               .mapValues(_.flatMap(_._2).toSet)
        )
      }

      val linkedNodes = (nodes ++ pkgNodes).map{ d =>
        val extraLinks = for{
          (value: Value.File, pos) <- d.dependencies
          acyclicPkg <- acyclicPkgs
          if nodeMap(value).value.pkg.startsWith(acyclicPkg.pkg)
          if !d.value.pkg.startsWith(acyclicPkg.pkg)
        } yield (acyclicPkg, pos)
        d.copy(dependencies = d.dependencies ++ extraLinks)
      }

      // only care about cycles with size > 1 here
      val components = DepNode.stronglyConnectedComponents(linkedNodes)
                              .filter(_.size > 1)

      val usedNodes = mutable.Set.empty[DepNode]
      for{
        c <- components
        n <- c
        if !usedNodes.contains(n)
        if acyclicFiles.toSeq.contains(n.value) || acyclicPkgs.contains(n.value)
      }{
        val cycle = DepNode.smallestCycle(n, c)
        val cycleInfo =
          (cycle :+ cycle.head).sliding(2)
                               .map{ case Seq(a, b) => (a.value, a.dependencies(b.value))}
                               .toSeq
        cycleReporter(
          cycleInfo.map{ case (a, b) => a -> b.map(_.line).toSet }
        )

        global.error("Unwanted cyclic dependency")
        for ((value, locs) <- cycleInfo){
          val blurb = value match{
            case Value.Pkg(pkg) => "package " + pkg.mkString(".")
            case Value.File(_, _) => ""
          }
          currentRun.units
                    .find(_.source.path == locs.head.source.path)
                    .get
                    .echo(locs.head, blurb)

          val otherLines = locs.tail
                               .map(_.line)
                               .filter(_ != locs.head.line)

          if (!otherLines.isEmpty){
            global.inform("More dependencies at lines " + otherLines.mkString(" "))
          }
        }

        usedNodes ++= cycle
      }
    }

    def name: String = "acyclic"
  }
}
