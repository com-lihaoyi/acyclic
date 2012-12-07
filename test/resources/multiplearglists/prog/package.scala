package multiplearglists.prog

object Injected extends sinject.Module[Injected]

class Injected(n: Int) {
  val twoArgs = new TwoArgLists("two")(1, 2)
  val threeArgs = new ThreeArgLists("three")("args")(1.5)
  val value = n
  def get = twoArgs.get + " " + threeArgs.get
}
