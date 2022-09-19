import mill._, scalalib._, publish._
import $ivy.`de.tototec::de.tobiasroeser.mill.vcs.version::0.2.0`
import de.tobiasroeser.mill.vcs.version.VcsVersion

object acyclic extends Cross[AcyclicModule](
  "2.11.12",
  "2.12.8", "2.12.9", "2.12.10", "2.12.11", "2.12.12", "2.12.13", "2.12.14", "2.12.15", "2.12.16", "2.12.17",
  "2.13.0", "2.13.1", "2.13.2", "2.13.3", "2.13.4", "2.13.5", "2.13.6", "2.13.7", "2.13.8"
)
class AcyclicModule(val crossScalaVersion: String) extends CrossScalaModule with PublishModule {
  def crossFullScalaVersion = true
  def artifactName = "acyclic"
  def publishVersion = VcsVersion.vcsState().format()

  def pomSettings = PomSettings(
    description = artifactName(),
    organization = "com.lihaoyi",
    url = "https://github.com/com-lihaoyi/acyclic",
    licenses = Seq(License.MIT),
    versionControl = VersionControl.github(owner = "com-lihaoyi", repo = "acyclic"),
    developers = Seq(
      Developer("lihaoyi", "Li Haoyi", "https://github.com/lihaoyi")
    )
  )
  def compileIvyDeps = Agg(ivy"org.scala-lang:scala-compiler:$crossScalaVersion")

  object test extends Tests with TestModule.Utest {
    def sources = T.sources(millSourcePath / "src", millSourcePath / "resources")
    def ivyDeps = Agg(
      ivy"com.lihaoyi::utest:0.8.0",
      ivy"org.scala-lang:scala-compiler:$crossScalaVersion"
    )
  }
}
