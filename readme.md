Acyclic
=======
**Acyclic** is a Scala compiler acyclic.plugin that allows you to mark files within a build as `acyclic`, causing cyclic dependencies to become compilation errors.

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
import acyclic
```

And attempting to compile these files together will then result in a compilation error:

```
```

This applies to term-dependencies, type-dependencies, as well as cycles that span more than two files. Circular dependencies between files is something that people often don't want, but are difficult to avoid as introducing cycles is hard to detect while working or during code review, and **Acyclic** is designed to help you guard against unwanted cycles at compile-time.
