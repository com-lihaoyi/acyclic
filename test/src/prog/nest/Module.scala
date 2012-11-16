package prog.nest

@sinject.Module
class Module{
  implicit def m = this
  implicit val i = 10

  def run() = println("omg")
}


