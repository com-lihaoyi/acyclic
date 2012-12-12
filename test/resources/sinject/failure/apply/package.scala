package sinject.failure.apply

object Prog extends sinject.Module[Prog]

class Prog(x: Int, s: String) extends (() => String){
  def value = s
  def apply() = Other.get
}

object Other{
  def get = Prog().value
}
