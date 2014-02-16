package sinject

import utest._

object SinjectTester extends TestSuite{
  def tests = TestSuite{
    "simplestPossibleExample" - {
      TestUtils.make("simple.hello")
    }
  }
}


