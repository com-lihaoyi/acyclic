package nested.outer.inner

import nested.outer.Outer

class Cow() {
  def get() = Outer().value + Inner().value + 10
}
