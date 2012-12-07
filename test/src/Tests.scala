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
  "simplest possible example" in {
    assert(test("test/resources/simple", "simple.Simple") == "Two!lolOne! 10Two!wtfOne! 5")

  }
  "two-level nesting" in {
    assert(test("test/resources/nested", "nested.Nested") == "return: 29")
  }

  "injecting into a class with no argument lists" in {
    assert(test("test/resources/noarglists", "noarglists.NoArgLists") == "1 2")
  }

  "injecting into a class with multiple argument lists" in {
    assert(test("test/resources/multiplearglists", "multiplearglists.MultipleArgLists") == "two 4 three args 1 1.5 two 5 three args 2 1.5")
  }

  "injecting into a class with multiple constructors" in {
    assert(test("test/resources/multiconstructor", "multiconstructor.MultiConstructor") == "two 4 three args 1 1.5 two 5 three args 2 1.5")
  }
  def getFilePaths(src: String): List[String] = {
    val f = new io.File(src)
    if (f.isDirectory) f.list.toList.flatMap(x => getFilePaths(src + "/" + x))
    else List(src)
  }
  def test(src: String, s: String) = {

    new java.io.File("out/compiled").mkdirs()

    val settings = new Settings
    settings.d.value = "out/compiled"
    settings.Xprint.value = List("all")
    val classPath = getFilePaths("/Runtimes/scala-2.10.0-RC2/lib") :+
                    "out/production/plugin"

    println("classPath")
    classPath.map(new io.File(_).getAbsolutePath).foreach{ f =>
      settings.classpath.append(f)
      settings.bootclasspath.append(f)

      println(f)
    }

    println("sources")
    val sources = getFilePaths(src)
    sources.foreach(println)

    val compiler = new Global(settings, new ConsoleReporter(settings)){
      override protected def loadRoughPluginsList(): List[Plugin] = List(new SinjectPlugin(this))
    }

    println("Compiling...")
    val run = new compiler.Run()
    run.compile(sources)

    println("Executing...")
    val cl = new java.net.URLClassLoader(Array(new java.io.File("out/compiled/").toURI.toURL)){
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

    val cls = cl.loadClass(s)
    val result = cls.getMethod("run").invoke(null).asInstanceOf[String]
    println("RESULT: " + result)
    result
  }
}
