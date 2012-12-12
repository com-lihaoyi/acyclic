package sinject.success.nestedpackage

object Outer extends sinject.Module[Outer]
class Outer(n: Int) extends (() => String) {
  val value = n
  val innerThing = new inner.Inner(2)
  def apply() = ""+innerThing.get()
}
