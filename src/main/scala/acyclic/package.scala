
package object acyclic {
  /**
   * Import this within a file to make Acyclic verify that the file does not
   * have any circular dependencies with other files.
   */
  def file = ???

  /**
   * Import this within a package object to make Acyclic verify that the entire
   * package does not have any circular dependencies with other files or
   * packages. Circular dependencies *within* the package are Ok.
   */
  def pkg = ???
}
