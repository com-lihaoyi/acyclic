package sinject.simple

object Prog extends sinject.Module[Prog]{}

class Prog(x: Int, s: String) extends (() => String){

  val one = new ClassOne(x)
  val two = new ClassTwo(s)

  def apply() = one.run + " " + two.run
}


