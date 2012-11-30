package prog.nest

@sinject.Module
class Nest{
  implicit def m = this
  implicit val i = 10 / 0

  def run() = println("omg")
}


