package sinject.failure.apply


class apply(s: String){
  def value = "Two! " + s
  def run = Prog().value
}
