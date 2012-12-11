package sinject.inheritence

object Prog extends sinject.Module[Prog]{}

class Prog(x: Int, val s: String) extends (() => String){

  val inner = new Inner(s)
  val multi = new MultiInner(x)
  val value = x
  def apply() = inner.run + " : " + multi.get
}