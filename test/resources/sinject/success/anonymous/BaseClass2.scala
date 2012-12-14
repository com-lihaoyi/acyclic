package sinject.success.anonymous




class BaseClass2(val s: String) extends Trait with Trait2{
  def run = s + " " + Prog().value + " " + runTrait + " " + runTrait2
}
