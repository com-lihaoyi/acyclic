package sinject.success.traits
import Prog.dynamic

class BaseClass(s: String) extends Trait{
  def run = s + " " + Prog().value + " " + runTrait
}
