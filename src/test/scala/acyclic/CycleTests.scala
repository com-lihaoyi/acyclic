package acyclic

import utest._
import TestUtils.{make, makeFail}


object CycleTests extends TestSuite{

  def tests = TestSuite{
    "fail" - {
      "simple" - makeFail("fail/simple", Seq(
        "A.scala" -> Set(6),
        "B.scala" -> Set(4, 5)
      ))
      "indirect" - makeFail("fail/indirect", Seq(
        "A.scala" -> Set(6),
        "B.scala" -> Set(3),
        "C.scala" -> Set(4)
      ))
      "cyclicgraph" - makeFail("fail/cyclicgraph",
        Seq(
          "A.scala" -> Set(5),
          "E.scala" -> Set(5)
        ),
        Seq(
          "A.scala" -> Set(5),
          "E.scala" -> Set(6),
          "D.scala" -> Set(5)
        ),
        Seq(
          "A.scala" -> Set(5),
          "E.scala" -> Set(6),
          "D.scala" -> Set(6),
          "C.scala" -> Set(4)
        )
      )
    }
    "success" - {
      "simple" - make("success/simple")
      "cyclicunmarked" - make("success/cyclicunmarked")
      "dag" - make("success/dag")
    }
    "self" - make("../../main/scala")
  }
}


