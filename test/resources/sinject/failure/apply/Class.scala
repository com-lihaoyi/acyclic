package sinject.failure.apply


class Class(s: String){
  def value = "Two! " + s
  def run = Prog().value
}
