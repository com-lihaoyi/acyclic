package nested.outer.inner

import nested.outer.Outer


object Inner extends sinject.Module[Inner]

class Inner(){
  val value = Outer().value * 2
  val cow = new Cow()
  def get = cow.get
}