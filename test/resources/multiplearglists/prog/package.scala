package multiplearglists.prog

object Injected extends sinject.Module[Injected]

class Injected(n: Int) {
  val twoArgs = new TwoArgLists("two")(1, 2)
  val threeArgs = new ThreeArgLists("three")("args")(1.5)
  val fourArgs = new FourArgLists(1.5)('f', 12)(List("three", "args"))(128.toByte)
  val value = n
  def get = twoArgs.get + " | " + threeArgs.get + " | " + fourArgs.get
}
