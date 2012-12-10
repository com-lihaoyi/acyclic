package sinject.nestedpackage.inner


import sinject.nestedpackage.Outer
import sinject.nestedpackage.Outer.dynamic
object Inner extends sinject.Module[Inner]
class Inner(){
  val value = Outer().value * 2
  val cow = new Cow()
  def get() = cow.get()
}