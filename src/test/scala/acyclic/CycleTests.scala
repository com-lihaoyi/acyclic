package acyclic

import utest._
import TestUtils.make
import acyclic.TestUtils.CompilationException

object CycleTests extends TestSuite{
  def tests = TestSuite{
    "failsimple" - {
      val cycles = intercept[CompilationException]{
        make("fail.simple")
      }.cycles.distinct
      val expected = Seq(
        Seq(
          "src/test/resources/fail/simple/A.scala" -> Set(6),
          "src/test/resources/fail/simple/B.scala" -> Set(4, 5)
        )
      )
      assert(cycles == expected)
    }
    "successsimple" - {
      TestUtils.make("success.simple")
    }
  }
}


