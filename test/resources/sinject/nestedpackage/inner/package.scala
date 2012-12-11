package sinject.nestedpackage.inner


import sinject.nestedpackage.Outer
import sinject.nestedpackage.Outer.dynamic
object Inner extends sinject.Module[Inner]
class Inner(n: Int){
  val value = Outer().value * 2 + n
  val cow = new Cow()
  def get() = cow.get()
}