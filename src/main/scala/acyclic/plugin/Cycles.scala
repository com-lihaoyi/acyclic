package acyclic.plugin
import acyclic.file
import scala.tools.nsc.Global


trait Cycles{
  val global: Global
  import global._
  case class CycleNode(path: String, deps: Set[Position], acyclic: Boolean)
  object CycleNode{
    def canonicalize(cycle: Seq[CycleNode]): Seq[CycleNode] = {
      val startIndex = cycle.indexOf(cycle.minBy(_.path))
      cycle.drop(startIndex) ++ cycle.take(startIndex)
    }
  }
  case class DepNode(path: String,
                     dependencies: Map[String, Set[Position]],
                     acyclic: Boolean){
    def prettyPrint = Seq(
      path,
      "\t",
      if (acyclic) "acyclic" else "",
      dependencies.map{case (p, is) => "\n\t" + p + "\t" + is}.mkString
    ).mkString
  }

  object DepNode{
    def findCycle(nodesList: Seq[DepNode]): Stream[List[CycleNode]] = {
      val nodes = nodesList.map(n => n.path -> n).toMap

      def rec(node: DepNode, path: List[CycleNode]): Stream[List[CycleNode]] = {
        if (path.exists(_.path == node.path)) {
          if (!node.acyclic) Stream()
          else Stream(path.reverse.dropWhile(_.path != node.path))
        } else {
          def newNode(key: String) = {
            CycleNode(node.path, node.dependencies(key), node.acyclic)
          }
          node.dependencies
              .toStream
              .flatMap{ case (key, lines) => rec(nodes(key), newNode(key) :: path) }
        }
      }

      nodes.values.toStream.flatMap(rec(_, Nil))
    }

    def canonicalize(cycle: Seq[DepNode]): Seq[DepNode] = {
      val startIndex = cycle.indexOf(cycle.minBy(_.path))
      cycle.drop(startIndex) ++ cycle.take(startIndex)
    }
  }
}