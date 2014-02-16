package acyclic

import utest._

object SinjectTester extends TestSuite{
  def tests = TestSuite{
    "simplestPossibleExample" - {
      TestUtils.make("fail.simple")
    }
  }
}


