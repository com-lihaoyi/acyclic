organization  := "sinject"

name := "sinject"

version       := "0.1"

scalaVersion  := "2.10.0-RC2"

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "2.0.M4-B2" % "test" cross CrossVersion.full,
  "org.scala-lang" % "scala-compiler" % "2.10.0-RC2"
)

unmanagedSourceDirectories in Test <+= baseDirectory(_ / "src" / "test" / "resources")


