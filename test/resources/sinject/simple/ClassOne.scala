package sinject.simple

class ClassOne(n: Int) {
  def value = "One! " + n
  def run = Prog().two.value

}
