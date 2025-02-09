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
    forcePkg: => Boolean,
    fatal: => Boolean
) extends PluginComponent { t =>

  import global._

  val runsAfter = List("typer")

  override val runsBefore = List("patmat")

  val phaseName = "acyclic"

  private object base extends BasePluginPhase[CompilationUnit, Tree, Symbol] with GraphAnalysis[Tree] {
    protected val cycleReporter = t.cycleReporter
    protected lazy val force = t.force
    protected lazy val forcePkg = t.forcePkg
    protected lazy val fatal = t.fatal

    def treeLine(tree: Tree): Int = tree.pos.line
    def treeSymbolString(tree: Tree): String = tree.symbol.toString

    def reportError(msg: String): Unit = global.error(msg)
    def reportWarning(msg: String): Unit = global.warning(msg)
    def reportInform(msg: String): Unit = global.inform(msg)
    def reportEcho(msg: String, tree: Tree): Unit = global.reporter.echo(tree.pos, msg)

    def units: Seq[CompilationUnit] = global.currentRun.units.toSeq.sortBy(_.source.content.mkString.hashCode())
    def unitTree(unit: CompilationUnit): Tree = unit.body
    def unitPath(unit: CompilationUnit): String = unit.source.path
    def unitPkgName(unit: CompilationUnit): List[String] =
      unit.body.collect { case x: PackageDef => x.pid.toString }.flatMap(_.split('.'))
    def findPkgObjects(tree: Tree): List[Tree] = tree.collect { case x: ModuleDef if x.name.toString == "package" => x }
    def pkgObjectName(pkgObject: Tree): String = pkgObject.symbol.enclosingPackageClass.fullName
    def findPkgs(tree: Tree): List[Tree] = tree.collect { case x: PackageDef if x.symbol.fullName != "<empty>" => x }
    def pkgName(pkg: Tree): String = pkg.symbol.fullName
    def hasAcyclicImport(tree: Tree, selector: String): Boolean =
      tree.collect {
        case Import(expr, List(sel)) => expr.symbol.toString == "package acyclic" && sel.name.toString == selector
      }.exists(identity)

    def extractDependencies(unit: CompilationUnit): Seq[(Symbol, Tree)] = DependencyExtraction(global)(unit)
    def symbolPath(sym: Symbol): String = sym.sourceFile.path
    def isValidSymbol(sym: Symbol): Boolean = sym != NoSymbol && sym.sourceFile != null
  }

  override def newPhase(prev: Phase): Phase = new Phase(prev) {
    override def run(): Unit = base.runAllUnits()

    def name: String = "acyclic"
  }
}
