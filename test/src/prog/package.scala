package prog

object Prog{
  def apply()(implicit m: Prog = null) = m
  def init() = new Prog()
  val dynamic = "cow"
}

@sinject.Module
class Prog extends scala.annotation.Annotation{
  implicit def m = this
  implicit val i = 10
  def one = new ClassOne()
  def two = new ClassTwo()
}


