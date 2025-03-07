package acyclic

import acyclic.plugin.Value

import java.util.jar.JarFile
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

  def getJavaClasspathEntries(): Seq[String] = {
    System.getProperty("java.class.path")
      .split(java.io.File.pathSeparator)
      .toIndexedSeq
      .flatMap { f =>
        val extra = for {
          manifest <- Option.when(f.endsWith(".jar"))(new JarFile(f).getManifest()).toSeq
          mainAttr <- Option(manifest.getMainAttributes()).toSeq
          cp <-Option(mainAttr.getValue("Class-Path")).toSeq
          entry <- cp.split(" ")
          if entry.nonEmpty
        } yield entry match {
          case url if url.startsWith("file:///") =>
            url.substring("file://".length)
          case url if url.startsWith("file:/") =>
            url.substring("file:".length)
          case s => s
        }
        Seq(f) ++ extra
      }
  }
}
