package acyclic

import utest._
import TestUtils.{make, makeFail}
import scala.tools.nsc.util.ScalaClassLoader.URLClassLoader
import acyclic.plugin.Value.{Pkg, File}


object CycleTests extends TestSuite{

  def tests = TestSuite{
    "fail" - {

      "simple" - makeFail("fail/simple", Seq(
        File("B.scala") -> Set(4, 5),
        File("A.scala") -> Set(6)
      ))
      "indirect" - makeFail("fail/indirect", Seq(
        File("A.scala") -> Set(6),
        File("B.scala") -> Set(3),
        File("C.scala") -> Set(4)
      ))
      "cyclicgraph" - makeFail("fail/cyclicgraph",
        Seq(
          File("A.scala") -> Set(5),
          File("E.scala") -> Set(5)
        ),
        Seq(
          File("E.scala") -> Set(6),
          File("D.scala") -> Set(6),
          File("C.scala") -> Set(4, 5),
          File("A.scala") -> Set(5)
        )
      )
      "cyclicpackage" - makeFail("fail/cyclicpackage",
        Seq(
          Pkg("fail.cyclicpackage.b") -> Set(5),
          Pkg("fail.cyclicpackage.a") -> Set(5)
        )
      )
    }
    "success" - {
      "simple" - make("success/simple")
      "cyclicunmarked" - make("success/cyclicunmarked")
      "dag" - make("success/dag")
      "singlepackage" - make("success/singlepackage")
      "cyclicpackage" - make("success/cyclicpackage")
      "halfacyclicpackage" - make("success/halfacyclicpackage")
    }
    "self" - make("../../main/scala", extraIncludes = Nil)
  }
}


