package sinject.noarglists

object Injected extends sinject.Module[Injected]
class Injected(n: Int)  extends (() => String){
  val thing = new InnerClass
  val value = n
  def apply() = ""+thing.get
}
