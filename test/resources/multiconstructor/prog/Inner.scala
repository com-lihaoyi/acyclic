package multiconstructor.prog

class Inner(s: String) {
  def value = "Inner!" + s
  def run() = value + " " + myX.get

  val myX = new X(10)

  class X(n: Int){
    def get = n + Prog().value
  }
}
