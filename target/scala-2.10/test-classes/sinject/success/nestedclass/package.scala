package sinject.success.nestedclass

object Prog extends sinject.Module[Prog]{}

class Prog(x: Int, val s: String) {

  val inner = new Inner("c")
  val value = x
  def apply() = inner.run
}