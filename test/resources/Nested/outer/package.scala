package nested.outer

import inner.Inner

object Outer extends sinject.Module[Outer]
class Outer(n: Int) {
  val value = n
  val inner = new Inner()
  val get = inner.get
}
