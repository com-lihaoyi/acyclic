package sinject.success.inheritence

import sinject.success.inheritence.Prog.dynamic

class MultiInner(n: Int) extends MultiParent(n) {
  def get = Prog().s + " " + pGet + " " + gpGet + " " + n
}

class MultiParent(n: Int) extends MultiGrandParent{
  def pGet = Prog().s + (2 + n)
}

class MultiGrandParent{
  def gpGet = Prog().s + 4
}
