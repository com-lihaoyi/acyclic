package sinject.nestedpackage.inner
import Inner.dynamic

import sinject.nestedpackage.Outer
import Outer.dynamic

class Cow() {
  def get() = Outer().value + Inner().value + 10
}
