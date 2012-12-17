package sinject.success.anonymous

object Prog extends sinject.Module[Prog]

class Prog(x: Int, y: Int){
  val value = x
  val traitValue = y
  val cls = new BaseClass("cow"){
    override def run = "X" + super.run
  }
  val cls2 = new BaseClass2("dog"){
    override def run = "X" + super.run
  }
  val trt = new Trait{
    override def runTrait = super.runTrait * 2
  }
  val trt2 = new Trait2{
    override def runTrait2 = super.runTrait2 * 2
  }
  def apply() = cls.run + " | " + cls2.run + " | " + trt.runTrait + " | " + trt2.runTrait2
}


