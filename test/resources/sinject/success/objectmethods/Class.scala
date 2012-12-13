package sinject.success.objectmethods
import Prog.dynamic

class Class(s: String){
  def value = "Two! " + s
  def run = Prog().value
}
object Class{
  def get = Prog().value + 2
}
