package sinject.success.nestedpackage.inner
import Inner.dynamic

import sinject.success.nestedpackage.Outer
import Outer.dynamic

class Cow() {
  def get() = Outer().value + Inner().value + 10
}
