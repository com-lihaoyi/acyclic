package prog.nest

import sinject.Module

object Nest extends Module[Nest]
class Nest{
  implicit def m = this
  implicit val i = 10 / 0

  def run() = println("omg")
}


