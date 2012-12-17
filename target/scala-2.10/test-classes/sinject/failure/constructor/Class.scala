package sinject.failure.constructor

import Prog.dynamic

class Class(s: String){
  def value = "Two! " + s
  def run = Prog().value
}
