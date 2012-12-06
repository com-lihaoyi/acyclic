package simple.prog

object Prog extends sinject.Module[Prog]{}

class Prog(x: Int, s: String){
  implicit val iamcow = this

  val one = new ClassOne(x)
  val two = new ClassTwo(s)

  def run = {
    one.run() + two.run()
  }

}


