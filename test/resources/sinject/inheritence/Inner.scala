package sinject.inheritence
import Prog.dynamic

class Inner(s: String) extends Parent(s){


  def selfRun = "Self! " * Prog().value
  def run = selfRun + " " + parentRun

}

abstract class Parent(s: String){
  def parentRun = "Parent! " * Prog().value

}