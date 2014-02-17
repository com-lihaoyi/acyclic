
package acyclic.plugin
import acyclic.file

import scala.tools.nsc.{Global, Phase}
import tools.nsc.plugins.PluginComponent



/**
 * Injects implicit parameters into the top-level method and class signatures
 * throughout every compilation unit, based upon what imports it finds with
 * the name `dynamic` within the source file
 */
class PluginPhase(val global: Global, cycleReporter: Seq[Seq[(String, Set[Int])]] => Unit)
                  extends PluginComponent
                  with Cycles { t =>

  import global._

  val runsAfter = List("typer")

  override val runsRightAfter = Some("typer")

  val phaseName = "acyclic"

  override def newPhase(prev: Phase): Phase = new Phase(prev) {
    override def run() {

      val nodes = for (unit <- global.currentRun.units.toSeq) yield {
        val acyclic = unit.body.children.collect{
          case Import(expr, List(sel)) =>
            expr.symbol.toString == "package acyclic" && sel.name.toString == "file"
        }.exists(x => x)

        val deps = Dependencies(t.global)(unit)
        val connections = for{
          (sym, tree) <- deps
          if sym != NoSymbol
          if sym.sourceFile != null
          if sym.sourceFile.path != unit.source.path
        } yield sym.sourceFile.path -> tree.pos

        DepNode(
          unit.source.path,
          connections.groupBy(_._1)
                     .mapValues(_.map(_._2)),
          acyclic
        )
      }

      val cycles = DepNode.findCycle(nodes)
                          .map(CycleNode.canonicalize)
                          .distinct

      cycleReporter(cycles.map(_.map(n => n.path -> n.deps.map(_.line))))
      cycles.foreach{cycle =>
        global.error("Circular dependency between acyclic files:")
        for {
          cycNode <- cycle
          unit <- global.currentRun.units.find(_.source.path == cycNode.path)
        }{
          val firstDep :: rest = cycNode.deps.toList
          unit.echo(firstDep, if (cycNode.acyclic) "acyclic" else "")
          val filteredRest = rest.map(_.line).distinct.filter(_ != firstDep.line)
          if (filteredRest != Nil)
            global.inform("Other dependencies at lines: " + filteredRest.mkString(", "))
        }

      }
    }

    def name: String = "acyclic"
  }
}
