package noarglists.prog

object Injected extends sinject.Module[Injected]
class Injected(n: Int) {
  val thing = new InnerClass
  val value = n
  def get = thing.get
}
