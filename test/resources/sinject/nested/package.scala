package sinject.nested

object Outer extends sinject.Module[Outer]
class Outer(n: Int) extends (() => String) {
  val value = n
  val inner = new sinject.nested.inner.Inner()
  def apply() = ""+inner.get()
}
