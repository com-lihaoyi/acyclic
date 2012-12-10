package sinject.existingimplicit

object Injected extends sinject.Module[Injected]

class Injected(n: Int, implicit val s: String, implicit val d: Double) extends (() => String){

  val nli = new NoListsImplicit
  val nlti = new NoListsTwoImplicits
  val oli = new OneListImplicit(n)
  val olti = new OneListTwoImplicits(n)
  val tlti = new TwoListTwoImplicits(n, 64.toByte)('c', 10)

  val value = n

  def apply() = nli.get + " | " + nlti.get + " | " + oli.get + " | " + olti.get + " | " + tlti.get
}
