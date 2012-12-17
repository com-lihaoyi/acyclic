package sinject.success.anonymous

import Prog.dynamic

class BaseClass2(val s: String) extends Trait with Trait2{
  def run = s + " " + Prog().value + " " + runTrait + " " + runTrait2
}
