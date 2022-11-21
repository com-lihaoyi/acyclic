package acyclic

import utest._
import TestUtils.{make, makeFail}
import scala.tools.nsc.util.ScalaClassLoader.URLClassLoader
import acyclic.plugin.Value.{Pkg, File}
import scala.collection.SortedSet
import acyclic.file

object CycleTests extends TestSuite {

  def tests = Tests {
    test("fail") - {
      test("simple") - makeFail("fail/simple")(Seq(
        File("B.scala") -> SortedSet(4, 5),
        File("A.scala") -> SortedSet(5)
      ))

      test("indirect") - makeFail("fail/indirect")(Seq(
        File("A.scala") -> SortedSet(6),
        File("B.scala") -> SortedSet(3),
        File("C.scala") -> SortedSet(4)
      ))
      test("cyclicgraph") - makeFail("fail/cyclicgraph")(
        Seq(
          File("A.scala") -> SortedSet(5),
          File("E.scala") -> SortedSet(6),
          File("D.scala") -> SortedSet(6),
          File("C.scala") -> SortedSet(4, 5)
        )
      )
      test("cyclicpackage") - makeFail("fail/cyclicpackage")(
        Seq(
          Pkg("fail.cyclicpackage.b") -> SortedSet(5),
          Pkg("fail.cyclicpackage.a") -> SortedSet(5)
        )
      )
      test("halfpackagecycle") - makeFail("fail/halfpackagecycle")(Seq(
        File("B.scala") -> SortedSet(3),
        File("A.scala") -> SortedSet(4),
        Pkg("fail.halfpackagecycle.c") -> SortedSet(5)
      ))
    }
    test("success") - {
      test("simple") - make("success/simple")
      test("ignorejava") - make("success/java")
      test("cyclicunmarked") - make("success/cyclicunmarked")
      test("dag") - make("success/dag")
      test("pkg") {
        test("single") - make("success/pkg/single")
        test("mutualcyclic") - make("success/pkg/mutualcyclic")
        test("halfacyclic") - make("success/pkg/halfacyclic")
        test("innercycle") - make("success/pkg/innercycle")
      }
    }
    test("self") - make("../../src", extraIncludes = Nil)
    test("force") - {
      test("warn") - {
        test("fail") - {
          make("force/simple", force = true, warn = true).exists {
            case (_, "Unwanted cyclic dependency", "warning") => true
            case _ => false
          }
        }
      }
      test("fail") - makeFail("force/simple", force = true)(Seq(
        File("B.scala") -> SortedSet(4, 5),
        File("A.scala") -> SortedSet(4)
      ))
      test("pass") - make("force/simple")
      test("skip") - make("force/skip", force = true)
    }
  }
}
