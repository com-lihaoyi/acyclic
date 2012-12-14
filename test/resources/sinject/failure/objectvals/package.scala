package sinject.failure.objectvals


object Prog extends sinject.Module[Prog]

class Prog(x: Int, s: String) extends (() => String){
  def value = s
  def apply() = Class.get
}
