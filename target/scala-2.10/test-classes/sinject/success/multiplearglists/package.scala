package sinject.success.multiplearglists

object Injected extends sinject.Module[Injected]

class Injected(n: Int) {

  val noArgs = new NoArgLists
  val twoArgs = new TwoArgLists("two")(1, 2)
  val threeArgs = new ThreeArgLists("three")("args")(1.5)
  val fourArgs = new FourArgLists(1.5)('f', 12)(List("three", "args"))(128.toByte)
  val value = n

  def apply() = noArgs.get + " | " + twoArgs.get + " | " + threeArgs.get + " | " + fourArgs.get
}
