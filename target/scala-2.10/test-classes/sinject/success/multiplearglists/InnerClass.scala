package sinject.success.multiplearglists
import Injected.dynamic

class NoArgLists{
  def get = Injected().value
}

class TwoArgLists(s: String)(x: Int, y: Int){
  def get = s + " " + (Injected().value + x + y)
}

class ThreeArgLists(s: String)(ss: String)(d: Double){
  def get = s + " " + ss + " " + Injected().value + " " + d
}


class FourArgLists(d: Double)(c: Char, i: Int)(l: List[String])(b: Byte){
  def get = d + " " + c + " " + i + " " + l + " " + b + " " + Injected().value
}
