package sinject.nested.inner

import sinject.nested.Outer

class Cow() {
  def get() = Outer().value + Inner().value + 10
}
