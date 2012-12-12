package sinject.failure.simple

import Prog.dynamic
import annotation.implicitNotFound


class Class(s: String){
  def value = "Two! " + s
  def run = Prog().value
}
