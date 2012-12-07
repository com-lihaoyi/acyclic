package multiconstructor.prog

class Inner(s: String) {
  def value = "Inner!" + s
  def run() = value + " " + Prog().value
  def this(i: Int, c: Char){
    this(c + " " + i)
  }
}
