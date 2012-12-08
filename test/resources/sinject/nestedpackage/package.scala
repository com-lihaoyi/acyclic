package sinject.nestedpackage

object Outer extends sinject.Module[Outer]
class Outer(n: Int) extends (() => String) {
  val value = n
  val inner = new sinject.nestedpackage.inner.Inner()
  def apply() = ""+inner.get()
}
