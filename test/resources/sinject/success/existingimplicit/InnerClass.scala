package sinject.success.existingimplicit
import Injected.dynamic

class NoListsImplicit(implicit s: String){
  def get = s + " " + Injected().value
}
class NoListsTwoImplicits(implicit s: String, d: Double){
  def get = s + " " + Injected().value + " " + d
}
class OneListImplicit(n: Int)(implicit s: String){
  def get = s + " " + Injected().value + " " + n
}

class OneListTwoImplicits(n: Int)(implicit s: String, d: Double){
  def get = s + " " + Injected().value + " " + n + " " + d
}

class TwoListTwoImplicits(n: Int, b: Byte)(c: Char, i: Int)(implicit s: String, d: Double){
  def get = s + " " + Injected().value + " " + n + " " + d + " " + b + " " + c + " " + i
}
