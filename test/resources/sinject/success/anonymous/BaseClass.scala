package sinject.success.anonymous

class BaseClass(val s: String) extends Trait{
  def run = s + " " + Prog().value + " " + runTrait
}
