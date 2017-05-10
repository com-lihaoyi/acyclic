Acyclic [![Join the chat at https://gitter.im/lihaoyi/acyclic](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/lihaoyi/acyclic?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
===================================================

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

In this case it is very obvious that there is a circular dependency, but in larger projects the fact that a circular dependency exists can be difficult to spot. With **Acyclic**, you can annotate either source file with an `acyclic` import:

```scala
package fail.simple
import acyclic.file

class A {
  val b: B = null
}
```

And attempting to compile these files together will then result in a compilation error:

```scala
error: Unwanted cyclic dependency

src/test/resources/fail/simple/B.scala:4:
  val a1: A = new A
                  ^
symbol: class A
More dependencies at lines 5

src/test/resources/fail/simple/A.scala:6:
  val b: B = null
      ^
symbol: class B

```

This applies to term-dependencies, type-dependencies, as well as cycles that span more than two files. Circular dependencies between files is something that people often don't want, but are difficult to avoid as introducing cycles is hard to detect while working or during code review. **Acyclic** is designed to help you guard against unwanted cycles at compile-time, and tells you exactly where the cycles are when they appear so you can deal with them.

A more realistic example of a cycle that **Acyclic** may find is this one taken from a cycle in [uTest](https://github.com/lihaoyi/utest):

```scala
[error] Circular dependency between acyclic files:
[info] /Users/haoyi/Dropbox (Personal)/Workspace/utest/shared/main/scala/utest/Formatter.scala:58: acyclic
[info]       val traceStr = r.value match{
[info]                        ^
[info] Other dependencies at lines: 20, 54, 44, 66, 40, 15, 67, 2, 45, 42
[info] /Users/haoyi/Dropbox (Personal)/Workspace/utest/shared/main/scala/utest/framework/Model.scala:76:
[info]           v.runAsync(onComplete, path :+ i, strPath :+ v.value.name, thisError)
[info]           ^
[info] Other dependencies at lines: 120
[info] /Users/haoyi/Dropbox (Personal)/Workspace/utest/shared/main/scala/utest/package.scala:72:
[info]   val TestSuite = framework.TestSuite
[info]       ^
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

**Acyclic** also allows you to annotate entire packages as `acyclic` by placing a `import acyclic.pkg` inside the package object. Consider the following set of files:

```scala
// c/C1.scala
package fail.halfpackagecycle.c

object C1
```
```scala
// c/C2.scala
package fail.halfpackagecycle
package c

class C2 {
  lazy val b = new B
}
```
```scala
// c/package.scala
package fail.halfpackagecycle

package object c {
  import acyclic.pkg
}
```
```scala
// A.scala
package fail.halfpackagecycle

class A {
  val thing = c.C1
}
```
```scala
// B.scala
package fail.halfpackagecycle

class B extends A
```

These 5 files do not have any file-level cycles, and form a nice linear dependency chain:

```
c/C2.scala -> B.scala -> A.scala -> c/C1.scala
```

However, we may want to preserve the invariant that the package `c` does not have any cyclic dependencies with other packages or files.. By annotating the package with `import acyclic.pkg` in its package objects as shown above, we can make this circular package dependency error out:

```scala
error: Unwanted cyclic dependency

src/test/resources/fail/halfpackagecycle/B.scala:3:
class B extends A
        ^
symbol: constructor A

src/test/resources/fail/halfpackagecycle/A.scala:4:
  val thing = c.C1
      ^
symbol: object C1

package fail.halfpackagecycle.c
src/test/resources/fail/halfpackagecycle/c/C2.scala:5:
  lazy val b = new B
           ^
symbol: class B
```

Since, `c` as a whole must be acyclic, the dependency cycle between `c`, `B.scala` and `A.scala` is prohibited, and **Acyclic** errors out. As you can see, it tells you exactly where the dependencies are in the source files, giving you an opportunity to find and remove them. Here's a realistic example from Scala.Rx:

```scala
[error] Unwanted cyclic dependency
[info]
[info] /Users/haoyi/Dropbox (Personal)/Workspace/scala.rx/shared/main/scala/rx/core/Dynamic.scala:10:
[info] import rx.ops.Spinlock
[info]        ^
[info] symbol: value <import>
[info] More dependencies at lines 29 60 33 41 27 23
[info]
[info] package rx.ops
[info] /Users/haoyi/Dropbox (Personal)/Workspace/scala.rx/shared/main/scala/rx/ops/Async.scala:78:
[info]       super.ping(incoming)
[info]             ^
[info] symbol: method ping
[info] More dependencies at lines 69 101 97 95 67
```

As you can see, `Dynamic.scala` in `rx.core` was accidentally depending on `Spinlock` in `rx.ops`. That cross-module dependency from `rx.core` to `rx.ops` should not exist, and the proper solution was to move `Spinlock` over to `rx.core`. Without **Acyclic**, this circular dependency would likely have gone un-noticed.

How to Use
==========

To use, add the following to your `build.sbt`:

```scala
libraryDependencies += "com.lihaoyi" %% "acyclic" % "0.1.7" % "provided"

autoCompilerPlugins := true

addCompilerPlugin("com.lihaoyi" %% "acyclic" % "0.1.7")
```

If you are on Scala 2.10.x, you may need an additional compile-time dependency:

```scala
"org.scala-lang" % "scala-compiler" % scalaVersion.value % "provided" // needed for 2.10.x only
```

**Acyclic** is currently being used in [uTest](https://github.com/lihaoyi/utest), [Scalatags](https://github.com/lihaoyi/scalatags) and [Scala.Rx](https://github.com/lihaoyi/scala.rx), and helped remove many cycle between files which had no good reason for being cyclic. It is also being used to verify the acyclicity of [its own code](https://github.com/lihaoyi/acyclic/blob/master/src/main/scala/acyclic/plugin/PluginPhase.scala#L3). It works with Scala 2.10, 2.11 and 2.12.

If you're using incremental compilation, you may need to do a clean compile for **Acyclic** to find all unwanted cycles in the compilation run.

Force
=====

If you want to enforce acyclicity across *all* your files, you can pass in the
command-line compiler flag:

```
-P:acyclic:force 
```

Or via SBT:

```scala
scalacOptions += "-P:acyclic:force"
```

This will make the acyclic plugin complain if *any* file in your project is involved
in an import cycle, without needing to annotate everything with 
`import acyclic.file`. If you want to white-list a small number of files whose
cycles you've decided are OK, you can use

```scala
import acyclic.skipped
```

to tell the acyclic plugin to ignore them.

Warnings instead of errors
==========================

If you want the plugin to only emit warnings instead of errors, add `warn` to the plugin's flags.

```scala
scalacOptions += "-P:acyclic:warn"
```

Limitations
===========

Acyclic has problems in a number of cases:

- If you use curly-braced `package XXX {}` acyclic inside your source files, it does the wrong thing. Acyclic assumes all packages are listed in a sequence of statements at the top of each file
- Under incremental compilation, Acyclic does not always find all possible cycles, since one cycles within the files currently getting compiled will get caught. A solution is to do a clean build every once in a while.

ChangeLog
=========

**0.1.7**: Fix `import acyclic.skipped`, which was broken in 0.1.6
 
**0.1.6**: You can now use the scalac option `-P:acyclic:force` 
(`scalaOptions += "-P:acyclic:force"` in SBT) to enforce acyclicity across
your entire codebase.

**0.1.5**: Scala 2.12.x support

**0.1.4**: Loosen restrictions on compiler plugin placement, to allow better 
interactions with other plugins. Also, `acyclic.file` is now `@compileTimeOnly` 
to provide better errors

**0.1.3**: Ignore, but don't crash, on Java sources

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
