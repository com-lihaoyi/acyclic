//acyclic

package acyclic.plugin

import scala.tools.nsc.{Global, Phase}
import tools.nsc.plugins.PluginComponent

case class DepNode(path: String,
                   dependencies: Map[String, Set[Int]],
                   acyclic: Boolean){
  def prettyPrint = Seq(
    path,
    "\t",
    if (acyclic) "acyclic" else "",
    dependencies.map{case (p, is) => "\n\t" + p + "\t" + is}.mkString
  ).mkString
}

object DepNode{
  def findCycle(nodesList: Seq[DepNode]): Stream[List[DepNode]] = {
    val nodes = nodesList.map(n => n.path -> n).toMap

    def rec(node: DepNode, path: List[DepNode]): Stream[List[DepNode]] = {
      if (path.exists(_.path == node.path) && node.acyclic) {
        Stream(path.reverse.dropWhile(_ != node))
      } else {
        def newNode(key: String) = node.copy(dependencies = Map(key -> node.dependencies(key)))
        node.dependencies
            .toStream
            .flatMap{ case (key, lines) => rec(nodes(key), newNode(key) :: path) }
      }
    }

    nodes.values.toStream.flatMap(rec(_, Nil))
  }
}


/**
 * Injects implicit parameters into the top-level method and class signatures
 * throughout every compilation unit, based upon what imports it finds with
 * the name `dynamic` within the source file
 */
class PluginPhase(val global: Global, cycleReporter: Seq[Seq[(String, Set[Int])]] => Unit) extends PluginComponent { t =>


  import global._

  val runsAfter = List("typer")
  override val runsRightAfter = Some("typer")

  val phaseName = "acyclic"

  override def newPhase(prev: Phase): Phase = new Phase(prev) {
    override def run() {
      val nodes = for (unit <- global.currentRun.units.toSeq) yield {
        val deps = Dependencies(t.global)(unit)
        val connections = for{
          (sym, tree) <- deps
          if sym != NoSymbol
          if sym.sourceFile != null
          if sym.sourceFile.path != unit.source.path
        } yield sym.sourceFile.path -> tree.pos.line

        DepNode(
          unit.source.path,
          connections.groupBy(_._1)
                     .mapValues(_.map(_._2)),
          unit.source.content.mkString.contains("//acyclic")
        )
      }

      val cycles = DepNode.findCycle(nodes)
      cycleReporter(cycles.map(_.map(n => n.path -> n.dependencies.values.head)))
      cycles.headOption.foreach{cycle =>
        global.error(
          cycle.map(_.prettyPrint).mkString("\n")
        )
      }
    }

    def name: String = "acyclic"
  }
}
