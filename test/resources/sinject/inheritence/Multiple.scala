package sinject.inheritence

import sinject.inheritence.Prog.dynamic

class MultiInner(n: Int) extends MultiParent(n) {
  def get = Prog().s + " " + pGet + " " + gpGet + " " + n
}

class MultiParent(n: Int) extends MultiGrandParent{
  def pGet = Prog().s + (2 + n)
}

class MultiGrandParent{
  def gpGet = Prog().s + 4
}
