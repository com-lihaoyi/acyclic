package prog.nest


object Nest extends sinject.Module[Nest]

class Nest{
  implicit val m = this
  implicit val i = 10.0 / 2

  val x = new Cow('a')
  def run() = println("omg")
}


