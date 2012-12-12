package sinject.success.traits

import Prog.dynamic
class BaseClass2(s: String) extends Trait with Trait2{
  def run = s + " " + Prog().value + " " + runTrait + " " + runTrait2
}
