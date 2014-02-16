
organization  := "sinject"

name := "sinject"

version       := "0.1"

scalaVersion  := "2.10.3"

libraryDependencies ++= Seq(
  "com.lihaoyi.utest" % "utest_2.10" % "0.1.1",
  "org.scala-lang" % "scala-compiler" % "2.10.3"
)

testFrameworks += new TestFramework("utest.runner.JvmFramework")

unmanagedSourceDirectories in Test <+= baseDirectory(_ / "src" / "test" / "resources")


