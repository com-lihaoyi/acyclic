package multiplearglists

import prog.Injected

object MultipleArgLists {

  def run() = {
    val packageOne = new Injected(1)
    val packageTwo = new Injected(2)
    packageOne.get + " " + packageTwo.get
  }
}
