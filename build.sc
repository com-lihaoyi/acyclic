import $ivy.`de.tototec::de.tobiasroeser.mill.vcs.version::0.4.0`

import mill._, scalalib._, publish._
import de.tobiasroeser.mill.vcs.version.VcsVersion

object Deps {
  def acyclicAgg(scalaVersion: String) =
    Agg(ivy"com.lihaoyi:::acyclic:0.3.9")
      .filter(_ => scalaVersion != "2.13.12" /* exclude unreleased versions, if any */ )

  def scalaCompiler(scalaVersion: String) = ivy"org.scala-lang:scala-compiler:${scalaVersion}"
  val utest = ivy"com.lihaoyi::utest:0.8.2"
}

val crosses =
  Seq("2.11.12") ++
    8.to(18).map("2.12." + _) ++
    0.to(12).map("2.13." + _)

object acyclic extends Cross[AcyclicModule](crosses)
trait AcyclicModule extends CrossScalaModule with PublishModule {
  override def crossFullScalaVersion = true
  override def artifactName = "acyclic"
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
  override def compileIvyDeps =
    Agg(Deps.scalaCompiler(crossScalaVersion)) ++
      Deps.acyclicAgg(crossScalaVersion)

  override def scalacPluginIvyDeps = Deps.acyclicAgg(crossScalaVersion)

  object test extends ScalaTests with TestModule.Utest {
    override def sources = T.sources(millSourcePath / "src", millSourcePath / "resources")
    override def ivyDeps = Agg(
      Deps.utest,
      Deps.scalaCompiler(crossScalaVersion)
    )
    override def scalacPluginIvyDeps = Agg.empty[Dep]
  }
}
