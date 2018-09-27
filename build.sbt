organization  := "com.lihaoyi"

name := "acyclic"

version := "0.1.8"

scalaVersion  := "2.11.8"

crossScalaVersions := Seq("2.10.7", "2.11.12", "2.12.6", "2.13.0-M5")

resolvers += Resolver.sonatypeRepo("releases")

libraryDependencies ++= Seq(
  "com.lihaoyi" %% "utest" % "0.6.6" % "test",
  "org.scala-lang" % "scala-compiler" % scalaVersion.value % "provided"
)

unmanagedSourceDirectories.in(Compile) ++= {
  CrossVersion.partialVersion(scalaBinaryVersion.value) match {
    case Some((2, n)) if n == 10 || n == 11 || n == 12 =>
      Seq(baseDirectory.value / "src" / "main" / "scala-2.10_2.12")
    case Some((2, 13)) =>
      Seq(baseDirectory.value / "src" / "main" / "scala-2.13")
    case _ =>
      Nil
  }
}

testFrameworks += new TestFramework("utest.runner.Framework")

unmanagedSourceDirectories in Test += baseDirectory.value / "src" / "test" / "resources"

// Sonatype
publishArtifact in Test := false

publishTo := Some("releases" at "https://oss.sonatype.org/service/local/staging/deploy/maven2")

scmInfo := Some(ScmInfo(
  browseUrl = url("https://github.com/lihaoyi/acyclic"),
  connection = "scm:git:git@github.com:lihaoyi/acyclic.git"
))

licenses := Seq("MIT" -> url("http://www.opensource.org/licenses/mit-license.html"))

homepage := Some(url("https://github.com/lihaoyi/acyclic"))

developers += Developer(
  email = "haoyi.sg@gmail.com",
  id = "lihaoyi",
  name = "Li Haoyi",
  url = url("https://github.com/lihaoyi")
)