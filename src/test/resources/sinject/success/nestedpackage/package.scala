package sinject.success.nestedpackage

object Outer extends sinject.Module[Outer]
class Outer(n: Int) {
  val value = n
  val innerThing = new inner.Inner(2)
  def apply() = ""+innerThing.get()
}
