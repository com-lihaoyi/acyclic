package sinject.success.simple

object Prog extends sinject.Module[Prog]

class Prog(x: Int, s: String) extends (() => String){
  lazy val one = new ClassOne(x)
  lazy val two = new ClassTwo(s)

  def apply() = one.run + " " + two.run
}


