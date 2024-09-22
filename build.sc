import $ivy.`de.tototec::de.tobiasroeser.mill.vcs.version::0.4.0`

import mill._, scalalib._, publish._
import de.tobiasroeser.mill.vcs.version.VcsVersion

object Deps {
  val scala211 = Seq("2.11.12")
  val scala212 = 8.to(20).map("2.12." + _)
  val scala213 = 0.to(14).map("2.13." + _)
  val scala33 = 0.to(3).map("3.3." + _)
  val scala34 = 0.to(3).map("3.4." + _)
  val scala35 = 0.to(0).map("3.5." + _)

  val unreleased = scala33 ++ scala34 ++ scala35

  def scalaCompiler(scalaVersion: String) =
    if (scalaVersion.startsWith("3.")) ivy"org.scala-lang::scala3-compiler:$scalaVersion"
    else ivy"org.scala-lang:scala-compiler:$scalaVersion"

  val utest = ivy"com.lihaoyi::utest:0.8.2"
}

val crosses =
  Deps.scala211 ++
    Deps.scala212 ++
    Deps.scala213 ++
    Deps.scala33 ++
    Deps.scala34 ++
    Deps.scala35

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
    Agg(Deps.scalaCompiler(crossScalaVersion))


  object test extends ScalaTests with TestModule.Utest {
    override def sources = T.sources(super.sources() :+ PathRef(millSourcePath / "resources"))
    override def ivyDeps = Agg(
      Deps.utest,
      Deps.scalaCompiler(crossScalaVersion)
    )
    override def scalacPluginIvyDeps = Agg.empty[Dep]
  }
}
