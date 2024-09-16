package acyclic

import acyclic.plugin.Value
import scala.collection.SortedSet

abstract class BaseTestUtils {
  val srcDirName: String

  /**
   * Attempts to compile a resource folder as a compilation run, in order
   * to test whether it succeeds or fails correctly.
   */
  def make(
      path: String,
      extraIncludes: Seq[String] = Seq("acyclic/src/acyclic/package.scala"),
      force: Boolean = false,
      warn: Boolean = false,
      collectInfo: Boolean = true
  ): Seq[(String, String)]

  def makeFail(path: String, force: Boolean = false)(expected: Seq[(Value, SortedSet[Int])]*): Unit

  case class CompilationException(cycles: Seq[Seq[(Value, SortedSet[Int])]]) extends Exception

  final def getFilePaths(src: String): List[String] = {
    val f = new java.io.File(src)
    if (f.isDirectory) f.list.toList.flatMap(x => getFilePaths(src + "/" + x))
    else List(src)
  }
}
