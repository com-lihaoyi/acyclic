package noarglists

import prog.Injected

/**
 * Created with IntelliJ IDEA.
 * User: Haoyi
 * Date: 12/6/12
 * Time: 11:25 PM
 * To change this template use File | Settings | File Templates.
 */
object NoArgLists {

  def run() = {
    val packageOne = new Injected(1)
    val packageTwo = new Injected(2)
    packageOne.get + " " + packageTwo.get
  }
}
