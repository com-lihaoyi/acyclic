package com.xmlite

import org.scalatest._
import reflect.ClassTag
import reflect.io.VirtualDirectory
import reflect.{ClassTag, classTag}
import scala.tools.nsc.{Settings, Global}
import scala.tools.nsc.reporters.ConsoleReporter
import scala.tools.nsc.plugins.Plugin
import plugin.SinjectPlugin
import sinject._


class XMLiteTester extends FreeSpec with ShouldMatchers{



  "All Tests" - {
    "simplest possible example" in {

      val first = make[simple.Prog](0: Integer, "fail")
      val second = make[simple.Prog](123: Integer, "cow")
      assert(first() === "Two! fail One! 0")
      assert(second() === "Two! cow One! 123")

    }
    "two-level folder/module nesting" in {
      val first = make[nestedpackage.Outer](1: Integer)
      val second = make[nestedpackage.Outer](5: Integer)
      assert(first() === "15")
      assert(second() === "27")
    }

    "class with multiple argument lists" in {
      val first = make[multiplearglists.Injected](3: Integer)
      val second = make[multiplearglists.Injected](5: Integer)
      assert(first() === "3 | two 6 | three args 3 1.5 | 1.5 f 12 List(three, args) -128 3")
      assert(second() === "5 | two 8 | three args 5 1.5 | 1.5 f 12 List(three, args) -128 5")
    }

    "class nested in class" in {
      val first = make[nestedclass.Prog](1: Integer, "mooo")
      val second = make[nestedclass.Prog](5: Integer, "cow")
      assert(first() === "Inner! c 11mooo")
      assert(second() === "Inner! c 15cow")
    }

    "classes with existing implicits" in {
      val first = make[existingimplicit.Injected](1: Integer, "mooo", 1.5: java.lang.Double)
      val second = make[existingimplicit.Injected](5: Integer, "cow", 0.9: java.lang.Double)
      assert(first() === "mooo 1 | mooo 1 1.5 | mooo 1 1 | mooo 1 1 1.5 | mooo 1 1 1.5 64 c 10")
      assert(second() === "cow 5 | cow 5 0.9 | cow 5 5 | cow 5 5 0.9 | cow 5 5 0.9 64 c 10")
    }



    "classes with traits" in {
      val first = make[traits.Prog](1: Integer, 2: Integer)
      val second = make[traits.Prog](5: Integer, 6: Integer)
      assert(first() === "cow 1 2 | dog 1 2 4")
      assert(second() === "cow 5 6 | dog 5 6 12")
    }
    "classes with inheritence" in {
      val first = make[inheritence.Prog](1: Integer, "first")
      val second = make[inheritence.Prog](5: Integer, "second")
      assert(first() === "Self! 1 Parent! 2")
      assert(second() === "Self! 5 Parent! 10")
    }
  }
  def getFilePaths(src: String): List[String] = {
    val f = new java.io.File(src)
    if (f.isDirectory) f.list.toList.flatMap(x => getFilePaths(src + "/" + x))
    else List(src)
  }
  lazy val settings = {
    val s =  new Settings
    s.d.value = "out/compiled"
    s.Xprint.value = List("all")
    val classPath = getFilePaths("/Runtimes/scala-2.10.0-RC2/lib") :+
      "out/production/plugin/"

    println("classPath")
    classPath.map(new java.io.File(_).getAbsolutePath).foreach{ f =>
      s.classpath.append(f)
      s.bootclasspath.append(f)
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
  lazy val vd = new VirtualDirectory("classFiles", None)
  /* Instantiates an object of type T passing the given arguments to its first constructor */
  def make[T: ClassTag](args: AnyRef*) = {

    new java.io.File("out/compiled").mkdirs()
    val src = "test/resources/" + classTag[T].runtimeClass.getPackage.getName.replace('.', '/')
    println("sources")
    val sources = getFilePaths(src)
    sources.foreach(println)

    println("Compiling...")
    val run = new compiler.Run()
    run.compile(sources)

    println("Executing...")
    val cls = cl.loadClass(classTag[T].runtimeClass.getName)
    cls.getConstructors()(0).newInstance(args:_*).asInstanceOf[() => String]
  }
}


