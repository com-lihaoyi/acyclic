package sinject

import sinject.TestUtils._
import org.scalatest.{ParallelTestExecution, FreeSpec}


class ErrorTester extends FreeSpec{

  "constructor outside of dynamic scope" in {
    intercept[CompilationException.type]{
      val first = make[failure.constructor.Prog](0: Integer, "fail")
    }
  }
  "apply outside of dynamic scope" in {
    intercept[NotInModuleError.type]{
      val first = make[failure.apply.Prog](0: Integer, "fail")
      first()
    }
  }
  "attempting to use `dynamic` function" in {
    intercept[UsingDynamicError.type]{
      val first = make[failure.usingdynamic.Prog](0: Integer, "fail")
      first()
    }
  }
  "attempting to use dynamic scope when creating val in object" in {
    intercept[NotInModuleError.type]{
      val first = make[failure.objectvals.Prog](0: Integer, "fail")
      first()
    }
  }


}
