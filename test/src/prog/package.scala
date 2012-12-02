package prog

object Prog extends sinject.Module[Prog]


class Prog{
  implicit def m = this
  implicit val i = 10
  def one = new ClassOne()
  def two = new ClassTwo()
}


