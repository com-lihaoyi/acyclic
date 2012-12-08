package nested

import outer.Outer

object Nested {
  def run() = {
    val outerA = new Outer(1)
    val outerB = new Outer(2)
    "return: " + (outerA.get() + outerB.get())
  }
}
