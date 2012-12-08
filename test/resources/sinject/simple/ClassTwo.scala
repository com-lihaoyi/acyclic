package sinject.simple

class ClassTwo(s: String) {
  def value = "Two! " + s
  def run = Prog().one.value

}
