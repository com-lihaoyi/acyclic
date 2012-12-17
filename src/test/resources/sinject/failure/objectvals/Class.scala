package sinject.failure.objectvals

class Class(s: String){
  def value = "Two! " + s
  def run = Prog().value
}

object Class{
  lazy val get = Prog().value
}
