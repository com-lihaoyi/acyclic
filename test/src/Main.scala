import prog.nest.Cow

object Main {
  def main(args: Array[String]) = {
    val m = new prog.Prog();
    println(m.one.run())
    println(m.two.run())
    implicit val c = 'c'
    implicit val l = 100l
    implicit val d = 0.0d
    implicit val a = Array("cow")
    val x = new Cow(1, "cow")(a, 1)
  }
}
