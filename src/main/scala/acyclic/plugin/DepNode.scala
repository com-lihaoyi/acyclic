package acyclic.plugin
import acyclic.file

case class DepNode(path: String,
                   dependencies: Map[String, Set[(Int, String)]],
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
      if (path.exists(_.path == node.path)) {
        if (!node.acyclic) Stream()
        else Stream(path.reverse.dropWhile(_.path != node.path))
      } else {
        def newNode(key: String) = node.copy(dependencies = Map(key -> node.dependencies(key)))
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