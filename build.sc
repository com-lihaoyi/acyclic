import mill._, scalalib._, publish._

object acyclic extends Cross[AcyclicModule]("2.12.8", "2.13.0")
class AcyclicModule(val crossScalaVersion: String) extends CrossScalaModule with PublishModule {
  def artifactName = "acyclic"
  def publishVersion = "0.2.0"

  def pomSettings = PomSettings(
    description = artifactName(),
    organization = "com.lihaoyi",
    url = "https://github.com/lihaoyi/Fansi",
    licenses = Seq(License.MIT),
    scm = SCM(
      "git://github.com/lihaoyi/Fansi.git",
      "scm:git://github.com/lihaoyi/Fansi.git"
    ),
    developers = Seq(
      Developer("lihaoyi", "Li Haoyi", "https://github.com/lihaoyi")
    )
  )
  def compileIvyDeps = Agg(ivy"org.scala-lang:scala-compiler:$crossScalaVersion")

  object test extends Tests {
    def testFrameworks = Seq("utest.runner.Framework")
    def sources = T.sources(millSourcePath / "src", millSourcePath / "resources")
    def ivyDeps = Agg(
      ivy"com.lihaoyi::utest:0.6.9",
      ivy"org.scala-lang:scala-compiler:$crossScalaVersion"
    )
  }
}