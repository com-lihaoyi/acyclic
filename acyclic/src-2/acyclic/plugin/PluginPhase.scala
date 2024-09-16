package acyclic.plugin
import acyclic.file
import acyclic.plugin.Compat._
import scala.collection.{SortedSet, mutable}
import scala.tools.nsc.{Global, Phase}
import tools.nsc.plugins.PluginComponent

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
    val global: Global,
    cycleReporter: Seq[(Value, SortedSet[Int])] => Unit,
    force: => Boolean,
    fatal: => Boolean
) extends PluginComponent { t =>

  import global._

  val runsAfter = List("typer")

  override val runsBefore = List("patmat")

  val phaseName = "acyclic"

  def pkgName(unit: CompilationUnit) = {
    unit.body
      .collect { case x: PackageDef => x.pid.toString }
      .flatMap(_.split('.'))
  }

  def units = global.currentRun
    .units
    .toSeq
    .sortBy(_.source.content.mkString.hashCode())

  private object base extends BasePluginPhase[Tree] with GraphAnalysis[Tree] {
    protected val cycleReporter = t.cycleReporter
    protected lazy val force = t.force
    protected lazy val fatal = t.fatal

    def treeLine(tree: Tree): Int = tree.pos.line
    def treeSymbolString(tree: Tree): String = tree.symbol.toString

    def reportError(msg: String): Unit = global.error(msg)
    def reportWarning(msg: String): Unit = global.warning(msg)
    def reportInform(msg: String): Unit = global.inform(msg)
    def reportEcho(msg: String, tree: Tree): Unit = global.reporter.echo(tree.pos, msg)

    def findAcyclics() = {
      val acyclicNodePaths = for {
        unit <- units
        if unit.body.children.collect {
          case Import(expr, List(sel)) =>
            expr.symbol.toString == "package acyclic" && sel.name.toString == "file"
        }.exists(x => x)
      } yield {
        Value.File(unit.source.path, pkgName(unit))
      }
      val skipNodePaths = for {
        unit <- units
        if unit.body.children.collect {
          case Import(expr, List(sel)) =>
            expr.symbol.toString == "package acyclic" && sel.name.toString == "skipped"
        }.exists(x => x)
      } yield {
        Value.File(unit.source.path, pkgName(unit))
      }

      val acyclicPkgNames = for {
        unit <- units
        pkgObject <- unit.body.collect { case x: ModuleDef if x.name.toString == "package" => x }
        if pkgObject.impl.children.collect { case Import(expr, List(sel)) =>
          expr.symbol.toString == "package acyclic" && sel.name.toString == "pkg"
        }.exists(x => x)
      } yield {
        Value.Pkg(
          pkgObject.symbol
            .enclosingPackageClass
            .fullName
            .split('.')
            .toList
        )
      }
      (skipNodePaths, acyclicNodePaths, acyclicPkgNames)
    }
  }

  override def newPhase(prev: Phase): Phase = new Phase(prev) {
    override def run() {
      val unitMap = units.map(u => u.source.path -> u).toMap
      val nodes = for (unit <- units) yield {

        val deps = DependencyExtraction(t.global)(unit)

        val connections = for {
          (sym, tree) <- deps
          if sym != NoSymbol
          if sym.sourceFile != null
          if sym.sourceFile.path != unit.source.path
        } yield (sym.sourceFile.path, tree)

        Node[Value.File, Tree](
          Value.File(unit.source.path, pkgName(unit)),
          connections.groupBy(c => Value.File(c._1, pkgName(unitMap(c._1))): Value)
            .mapValues(_.map(_._2))
            .toMap
        )
      }

      base.runOnNodes(nodes)
    }

    def name: String = "acyclic"
  }
}
