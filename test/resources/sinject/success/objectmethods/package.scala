package sinject.success.objectmethods

object Prog extends sinject.Module[Prog]

class Prog(implicit x: Int, s: String) extends (() => String){

  val inner = new Class(s)
  def value = s
  def apply() = inner.run + " " + Class.get + " " + Other.get + " " + Other2.getWithExistingImplicits
}


