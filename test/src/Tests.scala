import java.io
import java.net.{URLClassLoader, URL}
import org.scalatest.path.FreeSpec
import plugin.SinjectPlugin
import reflect.internal.util.{BatchSourceFile, SourceFile}
import reflect.io.{File, AbstractFile}
import tools.nsc.Global
import tools.nsc.plugins.Plugin
import tools.nsc.Settings
import tools.nsc.reporters.ConsoleReporter
import tools.nsc.util.ScalaClassLoader.URLClassLoader

class Tests extends FreeSpec{
  "testing injection" - {
    "simplest possible example" in {
      assert(test("test/resources/simple", "simple.Simple") == "Two!lolOne! 10Two!wtfOne! 5")
    }
    "two-level folder/module nesting" in {
      assert(test("test/resources/nested", "nested.Nested") == "return: 29")
    }

    "class with no argument lists" in {
      assert(test("test/resources/noarglists", "noarglists.NoArgLists") == "1 2")
    }

    "class with multiple argument lists" in {
      assert(test("test/resources/multiplearglists", "multiplearglists.MultipleArgLists") == "two 4 | three args 1 1.5 | 1.5 f 12 List(three, args) -128 1 || two 5 | three args 2 1.5 | 1.5 f 12 List(three, args) -128 2")
    }

    "nested class" in {
      assert(test("test/resources/multiconstructor", "multiconstructor.MultiConstructor") == "Inner! c 20 Inner! c 15")
    }
  }
  def getFilePaths(src: String): List[String] = {
    val f = new io.File(src)
    if (f.isDirectory) f.list.toList.flatMap(x => getFilePaths(src + "/" + x))
    else List(src)
  }
  lazy val settings = {
    val s =  new Settings
    s.d.value = "out/compiled"
    //s.Xprint.value = List("all")
    val classPath = getFilePaths("/Runtimes/scala-2.10.0-RC2/lib") :+
      "out/production/plugin"

    println("classPath")
    classPath.map(new io.File(_).getAbsolutePath).foreach{ f =>
      s.classpath.append(f)
      s.bootclasspath.append(f)

      println(f)
    }
    s
  }
  lazy val compiler = new Global(settings, new ConsoleReporter(settings)){
    override protected def loadRoughPluginsList(): List[Plugin] = List(new SinjectPlugin(this))
  }
  lazy val cl = new java.net.URLClassLoader(Array(new java.io.File("out/compiled/").toURI.toURL)){
    override protected def loadClass(name: String, resolve: Boolean): Class[_] = {
      try{
        println("Loading " + name)
        val cls = this.findClass(name)
        println("Done " + name)
        cls
      }catch{ case x: ClassNotFoundException =>
        println("Failed")
        super.loadClass(name, resolve)
      }
    }
  }
  def test(src: String, s: String) = {

    new java.io.File("out/compiled").mkdirs()

    println("sources")
    val sources = getFilePaths(src)
    sources.foreach(println)

    println("Compiling...")
    val run = new compiler.Run()
    run.compile(sources)

    println("Executing...")
    val cls = cl.loadClass(s)
    val result = cls.getMethod("run").invoke(null).asInstanceOf[String]
    println("RESULT: " + result)
    result
  }
}
