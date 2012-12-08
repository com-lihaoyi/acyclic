package sinject.nestedpackage.inner

import sinject.nestedpackage.Outer

class Cow() {
  def get() = Outer().value + Inner().value + 10
}
