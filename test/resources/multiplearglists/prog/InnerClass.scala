package multiplearglists.prog


class TwoArgLists(s: String)(x: Int, y: Int){
  def get = s + " " + (Injected().value + x + y)
}

class ThreeArgLists(s: String)(ss: String)(d: Double){
  def get = s + " " + ss + " " + Injected().value + " " + d
}

