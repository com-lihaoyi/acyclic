
organization  := "com.lihaoyi"

name := "acyclic"

version := "0.1.2"

scalaVersion  := "2.10.4"

crossScalaVersions := Seq("2.10.4", "2.11.0")

libraryDependencies ++= Seq(
  "com.lihaoyi" %% "utest" % "0.1.3" % "test",
  "org.scala-lang" % "scala-compiler" % scalaVersion.value % "provided"
)

testFrameworks += new TestFramework("utest.runner.JvmFramework")

unmanagedSourceDirectories in Test <+= baseDirectory(_ / "src" / "test" / "resources")

// Sonatype
publishArtifact in Test := false

publishTo <<= version { (v: String) =>
  Some("releases"  at "https://oss.sonatype.org/service/local/staging/deploy/maven2")
}

pomExtra := (
  <url>https://github.com/lihaoyi/acyclic</url>
    <licenses>
      <license>
        <name>MIT license</name>
        <url>http://www.opensource.org/licenses/mit-license.php</url>
      </license>
    </licenses>
    <scm>
      <url>git://github.com/lihaoyi/utest.git</url>
      <connection>scm:git://github.com/lihaoyi/acyclic.git</connection>
    </scm>
    <developers>
      <developer>
        <id>lihaoyi</id>
        <name>Li Haoyi</name>
        <url>https://github.com/lihaoyi</url>
      </developer>
    </developers>
  )
