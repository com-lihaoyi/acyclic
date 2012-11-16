package prog

class ClassOne()(implicit i: Int) {
  def value = "One! " + i
  def run() = Prog().two.value

}
