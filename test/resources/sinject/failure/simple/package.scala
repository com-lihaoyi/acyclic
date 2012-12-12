package sinject.failure.simple



object Prog extends sinject.Module[Prog]

class Prog(x: Int, s: String) extends (() => String){
  def value = s
  def apply() = Other.get
}

object Other{
  def get = new Class("cow").run
}
