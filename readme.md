Acyclic
=======
**Acyclic** is a Scala compiler plugin that allows you to mark files within a build as `acyclic`, turning circular dependencies between files into compilation errors.

For example, the following two files have a circular dependency between them:

```scala
package fail.simple

class A {
  val b: B = null
}
```
```scala
package fail.simple

class B {
  val a: A = null
}
```

In this case it is very obvious that there is a circular dependency, but in larger projects the fact that a circular dependency exists can be difficult to spot. Wih **Acyclic**, you can annotate either source file with an `acyclic` import:

```scala
package fail.simple
import acyclic.file

class A {
  val b: B = null
}
```

And attempting to compile these files together will then result in a compilation error:

```scala
error: Circular dependency between acyclic files:
src/test/resources/fail/simple/A.scala:6: acyclic
  val b: B = null
      ^
src/test/resources/fail/simple/B.scala:4:
  val a1: A = new A
              ^
```

This applies to term-dependencies, type-dependencies, as well as cycles that span more than two files. Circular dependencies between files is something that people often don't want, but are difficult to avoid as introducing cycles is hard to detect while working or during code review. **Acyclic** is designed to help you guard against unwanted cycles at compile-time.

A more realistic example of a cycle that **Acyclic** may find is this one taken from a cycle in [uTest](https://github.com/lihaoyi/utest):

```scala
[error] Circular dependency between acyclic files:
[info] /Users/haoyi/Dropbox (Personal)/Workspace/utest/shared/main/scala/utest/Formatter.scala:15:
[info]   def formatSingle(path: Seq[String], r: Result): String
[info]                                          ^
[info] Other dependencies at lines: 66, 20, 54, 44, 67, 40, 58, 2, 45, 42
[info] /Users/haoyi/Dropbox (Personal)/Workspace/utest/shared/main/scala/utest/framework/Model.scala:76:
[info]           v.runAsync(onComplete, path :+ i, strPath :+ v.value.name, thisError)
[info]           ^
[info] Other dependencies at lines: 120
[info] /Users/haoyi/Dropbox (Personal)/Workspace/utest/shared/main/scala/utest/package.scala:74: acyclic
[info]   type TestSuite  = framework.TestSuite
[info]                               ^
[info] Other dependencies at lines: 73
[info] /Users/haoyi/Dropbox (Personal)/Workspace/utest/shared/main/scala/utest/framework/TestSuite.scala:37:
[info]         log(formatter.formatSingle(path, s))
[info]                       ^
[info] Other dependencies at lines: 41, 33
```

As you can see, there is a dependency cycle between `Formatter.scala`, `Model.scala`, `package.scala` and `TestSuite.scala`. `package.scala` has been explicitly marked `acyclic`, and so compilation fails with an error. Apart from the line shown, **Acyclic** also gives other lines in the same file which contain dependencies contributing to this cycle.

Spotting this dependency cycle spanning 4 different files, and knowing exactly which pieces of code are causing it, is something that is virtually impossible to do manually via inspection or code-review. Using **Acyclic**, there is no chance of accidentally introducing a dependency cycle you don't want, and even when you do, it shows you exactly what's causing the cycle that you need to fix to make it go away.

Package Cycles
==============

**Acyclic** also allows you to annotate entire packages as `acyclic` by placing a `import acyclic.pkg` inside the package object. Consider two packages `a` and `b` with three files in each:

```scala
package fail.cyclicpackage
package a

class A1 extends b.B1
```
```scala
package fail.cyclicpackage.a
class A2
```
```scala
package fail.cyclicpackage

package object a {
  import acyclic.pkg
}
```
```scala
package fail.cyclicpackage.b
import acyclic.file
class B1
```
```scala
package fail.cyclicpackage
package b
import acyclic.file

class B2 extends a.A2
```
```scala
package fail.cyclicpackage

package object b {
  import acyclic.pkg
}

```

These 6 files do not have any file-level cycles: `a.A1` depends on `b.B1` and `b.B2` depends upon `a.A2`, forming two distinct, acyclic dependency graphs. However, we may want to preserve the invariant that the two packages `a` and `b` do not have any cyclic dependencies between them. By annotating the two packages as `acyclic.pkg` in their package objects as shown above, we can make this circular package dependency error out:

```scala
error: Unwanted cyclic dependency
src/test/resources/fail/cyclicpackage/a/A1.scala:5: package fail.cyclicpackage.a
class A1 extends b.B1{
                   ^
src/test/resources/fail/cyclicpackage/b/B2.scala:5: package fail.cyclicpackage.b
class B2 extends a.A2
         ^
```

As you can see, it tells you exactly where the dependencies are in the source file, giving you an opportunity to find and remove them.

How to Use
==========

To use, add the following to your `build.sbt`:

```scala
libraryDependencies += "com.lihaoyi.acyclic" %% "acyclic" % "0.1.0" % "provided"

autoCompilerPlugins := true

addCompilerPlugin("com.lihaoyi.acyclic" %% "acyclic" % "0.1.0")
```

**Acyclic** is currently being used in [uTest](https://github.com/lihaoyi/utest), [Scalatags](https://github.com/lihaoyi/scalatags) and [Scala.Rx](https://github.com/lihaoyi/scala.rx), and helped remove many cycle between files which had no good reason for being cyclic. It is also being used to verify the acyclicity of [its own code](https://github.com/lihaoyi/acyclic/blob/master/src/main/scala/acyclic/plugin/PluginPhase.scala#L3). It currently only supports Scala 2.10.

MIT License
===========

The MIT License (MIT)

Copyright (c) 2014 Li Haoyi

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.