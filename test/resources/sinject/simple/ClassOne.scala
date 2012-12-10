package sinject.simple
import Prog.dynamic

class ClassOne(n: Int){
  def value = "One! " + n
  def run = Prog().two.value

}
