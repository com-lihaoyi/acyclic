package sinject

import reflect._
import io.VirtualDirectory
import tools.nsc.io._
import tools.nsc.{Global, Settings}
import tools.nsc.reporters.ConsoleReporter
import tools.nsc.plugins.Plugin
import plugin.SinjectPlugin
import java.net.URLClassLoader

/**
 * Created with IntelliJ IDEA.
 * User: Haoyi
 * Date: 12/13/12
 * Time: 11:53 PM
 * To change this template use File | Settings | File Templates.
 */
object TestUtils {


  def getFilePaths(src: String): List[String] = {
    val f = new java.io.File(src)
    if (f.isDirectory) f.list.toList.flatMap(x => getFilePaths(src + "/" + x))
    else List(src)
  }

  /* Instantiates an object of type T passing the given arguments to its first constructor */
  def make[T: ClassTag](args: AnyRef*) = {


    val src = "src/test/resources/" + classTag[T].runtimeClass.getPackage.getName.replace('.', '/')
    val sources = getFilePaths(src)

    var vd = new VirtualDirectory("(memory)", None)
    lazy val cl = new ClassLoader(this.getClass.getClassLoader){
      override protected def loadClass(name: String, resolve: Boolean): Class[_] = {
        try{
          if (!name.startsWith("sinject")) throw new ClassNotFoundException()
          findClass(name)

        } catch { case e: Throwable =>
          try{
            getParent.loadClass(name)
          }catch{case e: Throwable =>
            e.printStackTrace()
            null
          }
        }
      }

      override protected def findClass(name: String): Class[_] = {
        Option(findLoadedClass(name)) getOrElse {

          val (pathParts :+ className) = name.split('.').toSeq

          val finalDir = pathParts.foldLeft(vd: AbstractFile)((dir, part) => dir.lookupName(part, true))

          finalDir.lookupName(className + ".class", false) match {
            case null   => throw new ClassNotFoundException()
            case file   =>
              val bytes = file.toByteArray
              this.defineClass(name, bytes, 0, bytes.length)
          }
        }
      }
    }

    lazy val settings = {
      val s =  new Settings
      //s.Xprint.value = List("all")
      val classPath = getFilePaths("/Runtimes/scala-2.10.0-RC2/lib") :+
        "target/scala-2.10/classes"

      classPath.map(new java.io.File(_).getAbsolutePath).foreach{ f =>
        s.classpath.append(f)
        s.bootclasspath.append(f)
      }
      s.outputDirs.setSingleOutput(vd)
      s
    }
    lazy val compiler = new Global(settings, new ConsoleReporter(settings)){
      override protected def loadRoughPluginsList(): List[Plugin] = List(new SinjectPlugin(this))
    }
    val run = new compiler.Run()
    run.compile(sources)

    if (vd.toList.isEmpty) throw CompilationException

    val cls = cl.loadClass(classTag[T].runtimeClass.getName)

    cls.getConstructors()(0).newInstance(args:_*).asInstanceOf[{def apply(): String}]
  }
  object CompilationException extends Exception
}
