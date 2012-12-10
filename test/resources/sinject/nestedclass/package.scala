package sinject.nestedclass

object Prog extends sinject.Module[Prog]{}

class Prog(x: Int, val s: String) extends (() => String){

  val inner = new Inner("c")
  val value = x
  def apply() = inner.run
}