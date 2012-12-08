package multiconstructor.prog

object Prog extends sinject.Module[Prog]{}

class Prog(x: Int, s: String){

  val inner = new Inner("c")
  val value = x
  def run = inner.run
}


