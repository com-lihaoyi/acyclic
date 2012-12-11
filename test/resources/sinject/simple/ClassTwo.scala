package sinject.simple
import Prog.dynamic



class ClassTwo(s: String){
  def value = "Two! " + s
  def run = Prog().one.value
}
