package acyclic

import utest._
import TestUtils.make
import acyclic.TestUtils.CompilationException

object CycleTests extends TestSuite{

  def tests = TestSuite{
    "fail" - {
      "simple" - {
        TestUtils.makeFail("fail/simple", Seq(
            "src/test/resources/fail/simple/A.scala" -> Set(6),
            "src/test/resources/fail/simple/B.scala" -> Set(4, 5)
          )
        )
      }
    }
    "success" - {
      "simple" - {
        TestUtils.make("success/simple")
      }

    }
    "self" - {
      TestUtils.make("../../main/scala")
    }
  }
}


