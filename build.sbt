organization  := "com.lihaoyi"

name := "acyclic"

version := "0.1.8"

scalaVersion  := "2.11.8"

crossScalaVersions := Seq("2.10.6", "2.11.8", "2.12.0", "2.13.0-M1")

resolvers += Resolver.sonatypeRepo("releases")

libraryDependencies ++= Seq(
  "com.lihaoyi" %% "utest" % "0.4.7" % "test",
  "org.scala-lang" % "scala-compiler" % scalaVersion.value % "provided"
)

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