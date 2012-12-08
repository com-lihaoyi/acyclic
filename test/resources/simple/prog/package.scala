package simple.prog

object Prog extends sinject.Module[Prog]{}

class Prog(x: Int, s: String){

  val one = new ClassOne(x)
  val two = new ClassTwo(s)

  def run() = one.run + two.run
}


