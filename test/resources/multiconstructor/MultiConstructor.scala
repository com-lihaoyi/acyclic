package multiconstructor
object MultiConstructor{
  def run() = {
    val m = new prog.Prog(10, "lol")

    val n = new prog.Prog(5, "wtf")
    m.run + n.run
  }
}
