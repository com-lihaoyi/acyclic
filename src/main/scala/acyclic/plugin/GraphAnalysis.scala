package acyclic.plugin
import acyclic.file
import scala.tools.nsc.Global
import collection.mutable
trait GraphAnalysis{
  val global: Global
  import global._

  case class DepNode(path: String, pkg: List[String], dependencies: Map[String, Set[Position]]){
    override def toString = s"DepNode($path)"
  }

  object DepNode{
    /**
     * Does a double Breadth-First-Search to find the shortest cycle starting
     * from `from` within the DepNodes in `among`.
     */
    def smallestCycle(from: DepNode, among: Set[DepNode]): Seq[DepNode] = {
      val nodeMap = among.map(n => n.path -> n).toMap
      val distances = mutable.Map(from -> 0)
      val queue = mutable.Queue(from)
      while(queue.nonEmpty){
        val next = queue.dequeue()
        val children = next.dependencies
                           .keys
                           .collect(nodeMap)
                           .filter(!distances.contains(_))

        children.foreach(distances(_) = distances(next) + 1)
        queue.enqueue(children.toSeq:_*)
      }
      var route = List(from)
      while(route.length == 1 || route.head != from){
        route ::= among.filter(x => x.dependencies.keySet.contains(route.head.path))
                       .minBy(distances)
      }
      route.tail
    }

    /**
     * Finds the strongly-connected components of the directed DepNode graph
     * by finding cycles in a Depth-First manner and collapsing any components
     * whose nodes are involved in the cycle.
     */
    def stronglyConnectedComponents(nodes: List[DepNode]): Set[Set[DepNode]] = {
      
      val nodeMap = nodes.map(n => n.path -> n).toMap

      val components = mutable.Map.empty[DepNode, Int] ++ nodes.zipWithIndex.toMap
      val visited = mutable.Set.empty[DepNode]

      nodes.foreach(n => rec(n, Nil))

      def rec(node: DepNode, path: List[DepNode]): Unit = {
        if (path.contains(node)) {
          val cycle = path.reverse
                          .dropWhile(_.path != node.path)
          
          val involved = cycle.map(components)
          val firstIndex = involved.head
          for ((n, i) <- components.toSeq){
            if (involved.contains(i)){
              components(n) = firstIndex
            }
          }
        } else if (!visited(node)) {
          visited.add(node)
          for((key, lines) <- node.dependencies){
            rec(nodeMap(key), node :: path)
          }
        }
      }

      components.groupBy{case (node, i) => i}
                .values
                .map(_.keys.toSet)
                .toSet
    }
  }
}