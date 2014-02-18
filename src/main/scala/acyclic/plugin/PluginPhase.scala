
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
class PluginPhase(val global: Global, cycleReporter: Seq[(String, Set[Int])] => Unit)
                  extends PluginComponent
                  with GraphAnalysis { t =>

  import global._

  val runsAfter = List("typer")

  override val runsRightAfter = Some("typer")

  val phaseName = "acyclic"
  def findAcyclicImport(s: String) = {

  }
  override def newPhase(prev: Phase): Phase = new Phase(prev) {
    override def run() {

      val nodes = for (unit <- global.currentRun.units.toList) yield {
        val pkgName = unit.body
                          .collect{case x: PackageDef => x.pid.toString}
                          .flatMap(_.split("\\."))

        val deps = DependencyExtraction(t.global)(unit)

        val connections = for{
          (sym, tree) <- deps
          if sym != NoSymbol
          if sym.sourceFile != null
          if sym.sourceFile.path != unit.source.path
        } yield (sym.sourceFile.path, tree.pos)

        DepNode(
          unit.source.path,
          pkgName,
          connections.groupBy(_._1)
                     .mapValues(_.map(_._2))
        )
      }

      val acyclicNodePaths = for {
        unit <- global.currentRun.units.toSet
        if unit.body.children.collect{
          case Import(expr, List(sel)) =>
            expr.symbol.toString == "package acyclic" && sel.name.toString == "file"
        }.exists(x => x)
      } yield unit.source.path

      val acyclicPkgNames = for {
        unit <- global.currentRun.units.toSeq
        pkgObject <- unit.body.collect{case x: ModuleDef if x.name.toString == "package" => x }
        if !pkgObject.impl.children.collect{case Import(expr, List(sel)) =>
          expr.symbol.toString == "package acyclic" && sel.name.toString == "pkg"
        }.isEmpty
      } yield pkgObject.symbol.enclosingPackageClass.fullName

      // only care about cycles with size > 1 here
      val components = DepNode.stronglyConnectedComponents(nodes)
                              .filter(_.size > 1)

      println("acyclicNodePaths\t" + acyclicNodePaths)
      println("acyclicPkgNames \t" + acyclicPkgNames)
      println("components")
      components.zipWithIndex.map{case (c, i) =>
        println(i)
        c.foreach(println)
      }

      val usedNodes = mutable.Set.empty[DepNode]
      for(c <- components){
        for (n <- c){
          if (!usedNodes.contains(n) && acyclicNodePaths(n.path)){
            val cycle = DepNode.smallestCycle(n, c)
            val cycleInfo =
              (cycle :+ cycle.head).sliding(2)
                                   .map{ case Seq(a, b) => (a.path, a.dependencies(b.path))}
                                   .toSeq
            cycleReporter(
              cycleInfo.map{ case (a, b) => a -> b.map(_.line).toSet }
            )
            global.error("Unwanted cyclic dependency")
            for ((path, locs) <- cycleInfo){

              currentRun.units
                        .find(_.source.path == path)
                        .get
                        .echo(locs.head, "")

              val otherLines = locs.tail
                                   .map(_.line)
                                   .filter(_ != locs.head.line)

              if (!otherLines.isEmpty){
                global.inform("More dependencies at lines " + otherLines.mkString(" "))
              }
            }

            usedNodes ++= cycle
            println("Cycle should be Acyclic! " + n)
            println(c)
            cycle.foreach(println)
          }
        }
      }
    }

    def name: String = "acyclic"
  }
}
