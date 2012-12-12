package sinject.success.nestedclass
import Prog.dynamic

class Inner(s: String) {
  def value = "Inner! " + s

  def selfRun = Prog().value
  def run = value + " " + myX.get
  val myX = new X(10)

  class X(n: Int){
    def get = n + Prog().value + Prog().s
  }
}
