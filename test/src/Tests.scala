import java.io
import java.net.{URLClassLoader, URL}
import org.scalatest.path.FreeSpec
import plugin.SinjectPlugin
import reflect.{ClassTag, classTag}
import reflect.internal.util.{BatchSourceFile, SourceFile}
import reflect.io.{File, AbstractFile}

import sinject._
import tools.nsc.Global
import tools.nsc.plugins.Plugin
import tools.nsc.Settings
import tools.nsc.reporters.ConsoleReporter
import tools.nsc.util.ScalaClassLoader.URLClassLoader

class Tests extends FreeSpec{
  "testing injection" - {
    "simplest possible example" in {

      val first = make[simple.Prog]("simple")(0: Integer, "fail")
      val second = make[simple.Prog]("simple")(123: Integer, "cow")
      assert(first() === "Two! fail One! 0")
      assert(second() === "Two! cow One! 123")

    }
    "two-level folder/module nesting" in {
      val first = make[nested.Outer]("nested")(1: Integer)
      val second = make[nested.Outer]("nested")(5: Integer)
      assert(first() === "13")
      assert(second() === "25")
    }

    "class with no argument lists" in {
      val first = make[noarglists.Injected]("noarglists")(2: Integer)
      val second = make[noarglists.Injected]("noarglists")(25: Integer)
      assert(first() === "2")
      assert(second() === "25")
    }

    "class with multiple argument lists" in {
      val first = make[multiplearglists.Injected]("multiplearglists")(3: Integer)
      val second = make[multiplearglists.Injected]("multiplearglists")(5: Integer)
      assert(first() === "two 6 | three args 3 1.5 | 1.5 f 12 List(three, args) -128 3")
      assert(second() === "two 8 | three args 5 1.5 | 1.5 f 12 List(three, args) -128 5")
    }

    "sinject.nested class" in {
      val first = make[nestedclass.Prog]("nestedclass")(1: Integer, "mooo")
      val second = make[nestedclass.Prog]("nestedclass")(5: Integer, "cow")
      assert(first() === "Inner! c 11mooo")
      assert(second() === "Inner! c 15cow")
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
      "out/production/plugin/"

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
      if (name.startsWith("sinject") && this.findLoadedClass(name) == null){
        println("Loading " + name)
        val cls = this.findClass(name)
        println("Done " + name)
        cls
      } else {
        super.loadClass(name, resolve)
      }
    }
  }
  def make[T: ClassTag](src: String)(args: AnyRef*) = {

    new java.io.File("out/compiled").mkdirs()

    println("sources")
    val sources = getFilePaths("test/resources/sinject/" + src)
    sources.foreach(println)

    println("Compiling...")
    val run = new compiler.Run()
    run.compile(sources)

    println("Executing...")
    val cls = cl.loadClass(classTag[T].runtimeClass.getName)
    cls.getConstructors()(0).newInstance(args:_*).asInstanceOf[() => String]
  }
}
