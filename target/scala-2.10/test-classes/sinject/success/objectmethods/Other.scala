package sinject.success.objectmethods

import Prog.dynamic
object Other {
  def get() = Prog().value + 3
}
object Other2{
  def getWithExistingImplicits(implicit i: Int) = Prog().value + i
}