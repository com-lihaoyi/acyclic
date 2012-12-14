package sinject.success.anonymous
import Prog.dynamic
class BaseClass(val s: String) extends Trait{
  def run = s + " " + Prog().value + " " + runTrait
}
