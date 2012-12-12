package sinject.success.traits

object Prog extends sinject.Module[Prog]

class Prog(x: Int, y: Int) extends (() => String){
  val value = x
  val traitValue = y
  val cls = new BaseClass("cow")
  val cls2 = new BaseClass2("dog")
  def apply() = cls.run + " | " + cls2.run
}


