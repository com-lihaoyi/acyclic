import mill._, scalalib._, publish._
import $ivy.`de.tototec::de.tobiasroeser.mill.vcs.version_mill0.9:0.1.1`
import de.tobiasroeser.mill.vcs.version.VcsVersion

object acyclic extends Cross[AcyclicModule]("2.11.12", "2.12.8", "2.13.0")
class AcyclicModule(val crossScalaVersion: String) extends CrossScalaModule with PublishModule {
  def artifactName = "acyclic"
  def publishVersion = VcsVersion.vcsState().format()

  def pomSettings = PomSettings(
    description = artifactName(),
    organization = "com.lihaoyi",
    url = "https://github.com/lihaoyi/acyclic",
    licenses = Seq(License.MIT),
    scm = SCM(
      "git://github.com/lihaoyi/acyclic.git",
      "scm:git://github.com/lihaoyi/acyclic.git"
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
      ivy"com.lihaoyi::utest:0.7.7",
      ivy"org.scala-lang:scala-compiler:$crossScalaVersion"
    )
  }
}
