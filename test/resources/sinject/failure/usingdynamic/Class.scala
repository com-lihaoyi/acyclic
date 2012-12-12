package sinject.failure.usingdynamic
import Prog.dynamic

class Class(s: String){
  def value = "Two! " + s
  def run = Prog().value + dynamic
}
