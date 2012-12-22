Sinject
=======
**Sinject** is a Scala compiler plugin that performs [dependency injection](http://en.wikipedia.org/wiki/Dependency_injection). This saves you the hassle of constantly passing commonly-used parameters from class to class and method to method, allowing you to turn a mess like this:

```scala
class Thing(d: Double)(ctx: Context){ ... new SecondThing(10)(ctx) ... }
class SecondThing(i: Int)(ctx: Context){ ... new Third("lol", 1.5)(ctx) ... }
class Third(s: String, d: Double)(ctx: Context){ ... doStuff(s)(ctx) ... }

object Module{
    def doStuff(s: String)(ctx: Context){ ... getValues(s.length)(ctx) ... }
    def getValues(i: Int)(ctx: Context) = ... /* actually use the Context */ ...
}
```

into

```scala
import Context.dynamic

class Thing(d: Double){ ... new SecondThing(10) ... }
class SecondThing(i: Int){ ... new Third("lol", 1.5) ... }
class Third(s: String, d: Double){ ... doStuff(s) ... }

object Module{
    def doStuff(s: String){ ... getValues(s.length) ... }
    def getValues(i: Int) = ... /* actually use the Context */ ...
}
```
Removing a whole lot of pollution from the signatures of your `class`s and `def`s, keeping your code [DRY](http://en.wikipedia.org/wiki/Don%27t_repeat_yourself).

Setup
=====


How to Use
==========
First, you need to define your `Context` class with a companion object extending `sinject.Module[T]`:

```scala
//context.scala
object Context extends sinject.Module[Context]
class Context{
    val thing = new Thing("lol", 2)
    val value = "rofl"
}
```

The `Context` class can be any old Scala class: there are no special requirements except that its companion must extend `sinject.Module[T]`. 

Now, in the file you want to inject the `Context` into, you place the marker import at the top of the file:

```scala
//injected.scala
import Context.dynamic

class Thing(v: String, i: Int){
    def print{
        println(v * i + Context().value)
    }
}
```

And when you do a:

```scala
new Context().thing.print
```

You will get

    lollolrofl    

As you can see, when you call `print` could grab the value "rofl" from the surrounding `Context` without you needing to add it to the parameter list explicitly. In fact, now that we are injecting `Context` into `Thing`, a `Thing` can only be instantiated within a `Context`. So if you try to do this

```scala
val thing = new Thing("lol", 2) // compile error
```

when not inside a `Context`, it will fail with a compile error.

When you only have a single `Thing`, 

```scala
import Context.dynamic
class Thing(){ ... }
```

is not much shorter than simply including an implicit parameter:

```scala
class Thing()(implicit ctx: Context){ ... }
```

In fact, this is exactly what Sinject converts it to under the covers. However, when you have a larger number of classes which all need this implicit `Context` to be passed in:

```scala
class Thing()(implicit ctx: Context){ ... }
class Person(name: String)(implicit ctx: Context){ ... }
class Cow(weight: Double)(implicit ctx: Context){ ... }
class Car(mileage: Double, capacity: Int)(implicit ctx: Context){ ... }
```

It starts getting somewhat annoying to have to keep declaring the implicit everwhere, and it's nice to only have to declare it once at the top of the file:

```scala
import Context.dynamic

class Thing(){ ... }
class Person(name: String){ ... }
class Cow(weight: Double){ ... }
class Car(mileage: Double, capacity: Int){ ... }
```

And have the `Context` automatically, implicitly passed in to every instance of these classes you create, conveniently accessible via `Context()`.

More
====

Transitivity
------------
The injection of contexts is transitive: if I create a class which gets the `Context` injected, and that class creates another class or calls a method which had the `Context` injected, the `Context` would be injected into them aswell:

```scala
import Context.dynamic
class Thing(){
    def print{
        new Second.print
    }
}

class Second{
    def print{
        println(v * i + Context().value)
    }
}
```

would also print

    lollolrofl    

Since `Thing` had the `Context` injected into it when it was created inside the `Context`, and `Second` got the context injected into it when it was created inside the `Thing`

`def`s
----
Sinject also injects the context into the method `def`s of static `object`s. e.g.

```scala
import Context.dynamic
object Cow{
    def moo{
        println(Context().value)
    }
}
```

using the same `Context` defined earlier, would print

    rofl

Again, this means you can only call `Cow.moo` when inside a `Context`, or inside a class which had the `Context` injected into it.

`trait`s
--------
If a trait has a `Context` injected into it, it can only be mixed into a class which also has the context mixed into it. e.g.

```scala
import Context.dynamic
trait MyTrait{ /* I can use Context() here */ }
```

can be mixed into

```scala
import Context.dynamic
class MyClass extends MyTrait{ ... } // this works
```

but cannot be mixed into

```scala
class NonInjectedClass extends MyTrait{ ... } // this doesn't work
```

Within the body of the trait, the current `Context` can be accessed as normal, via `Context()`.

Multiple Injections
-------------------
It's possible to have larger contexts which get injected into smaller contexts, which are themselves injected into the individual classes. For example:

```scala
// outer.scala
object Outer extends sinject.Module[Outer]
class Outer{ ... }

// inner.scala
import Outer.dynamic
object Inner extends sinject.Module[Inner]
class Inner{ /* I can use Outer() here */ }

// myclass.scala
import Inner.dynamic
import Outer.dynamic
class MyClass { /* I can use both Outer() and Inner() here */ }
```

As you would expect, an `Inner` can only be created inside the body of an `Outer`, and a `MyClass` can only be created within the body of an `Inner`. Inside `Inner`, you have `Outer()` to access the current `Outer`, and inside `MyClass`, you have both `Inner()` and `Outer()`, which would behave as you expect.

Limits
======
Sinject, in general, "just works" when passing context information around: It passes it from class to class through the constructor, from method to method through the parameter lists. It should "just work" for classes nested however deep, objects inside classes, anonymous classes, traits, classes inheriting from other classes, classes and methods with all kinds of parameter lists (None, Single, Multiple, with and without implicits).

However, there are still some rough edges:

static `val`s
-------------
Essentially the only place where the context cannot be passed is to static `val`s, which are defined on static `object`s. This is by design, as since these `val`s are evaluated just once, at load time, it is impossible to set it up such that different `context`s will see different versions of the `val`. Hence this

```scala
import Context.dynamic
object Thing{
    val x = Context().value + "lol"
}
```

will fail at compile time, saying it cannot find the an implicit `Context` in scope. Only static `object` `val`s have this problem: `val`s inside to `class`s, `trait`s or `def`s, or these nested every which way, should all work fine.

Why
===
There are several alternatives to this, Sinject, and I will go through them one by one to show why they are not satisfactory. The basic problem is to cut down on the plumbing needed to pass variables from scope to scope:

```scala
class Thing(d: Double)(ctx: Context){ ... new SecondThing(10)(ctx) ... }
class SecondThing(i: Int)(ctx: Context){ ... new Third("lol", 1.5)(ctx) ... }
class Third(s: String, d: Double)(ctx: Context){ ... doStuff(s)(ctx) ... }

object Module{
    def doStuff(s: String)(ctx: Context){ ... getValues(s.length)(ctx) ... }
    def getValues(i: Int)(ctx: Context) = ... /* actually use the Context */ ...
}
```

In this example, you can see that only `getValues` actually uses the `Context`, but in the end almost everyone needs to pass it around. This is a situation that comes up a lot, where your `Context` could be:

- Configuration information (language, etc.)
- Current User
- Current Request (for web servers)
- Database Connection
- Security Permissions

or countless other things. The overarching theme is that some large portion of the application needs this `Context` to function. For example, in a MVC (Model-View-Controller) website

- All the View code (HTML templates and related code) needs to know the current language
- All the Controller code needs to know the current User and Request, to know what it is allowed to do.
- All the Model code (e.g. your ORM) needs to know how to connect to the database

This becomes a problem, as suddenly every class and every method in your application has to start passing around these annoying (ctx: Context) variables. This is interesting, because there is nothing about this problem which is particular to Scala: people have been trying to find ways to avoid this for a long time, and so there are solutions out there, but I don't find any of them particularly satisfactory.

Pass it Everywhere
------------------
This is the approach taken by the Play! framework, as can be seen in [these](http://stackoverflow.com/questions/9751752/play-2-0-access-to-request-in-templates) [answers](http://stackoverflow.com/questions/9629250/how-to-avoid-passing-parameters-everywhere-in-play2) on stackoverflow. This is basically the problem I mentioned above.

This is pretty painful and results in the code I showed you above. The pain can be mitigated slightly by using implicits:

```scala
import Context.dynamic

class Thing(d: Double)(implicit ctx: Context){ ... new SecondThing(10) ... }
class SecondThing(i: Int)(implicit ctx: Context){ ... new Third("lol", 1.5) ... }
class Third(s: String, d: Double)(implicit ctx: Context){ ... doStuff(s) ... }

object Module{
    def doStuff(s: String)(implicit ctx: Context){ ... getValues(s.length) ... }
    def getValues(i: Int) = ... /* actually use the Context */ ...
}
```

But only slightly: You save the `ctx` at every call site, but you add a `implicit` at every delaration site, so it's not any less verbose and it's debatable whether or not it's actually a "win".

In comparison Sinject allows you to write

```scala
import Context.dynamic
class Thing(d: Double){ ... }
class SecondThing(i: Int){ ... }
class Third(s: String, d: Double){ ... }

object Module{
    def doStuff(s: String){ ... }
    def getValues(i: Int) = ...
}
```

Letting you only state the type once (at the top of the file) and having it automatically injected into every class and method in the file.

Magic Globals!
--------------
Another popular approach is to use global (thread-local) variables that always seem to magically have the value you need, when you need them. An example is the [Lift S variable](http://exploring.liftweb.net/master/index-9.html#sub:Advanced-S-Object), an object that magically always has your current Session when your code is executed, or the Akka `sender` variable, which always magically has the actor which sent you the last message.

```scala
def receive = {
  case SomeMessage =>
    sender ! Response
}
```

You just pull the `sender` variable out of thin air, and it always just has your last sender. What is actually happening is something like this

```scala
// request comes in
this.sender = ... // set S
this.receive(message) // use S
this.sender = null // unset S
```

Every time before the framework starts running your code, it will set the S object to the current Session, so all your code can see is the current Session. It then unsets it, and re-sets it to the new context when the next request comes in.

This goes against essentially all the things we learn about structured programming: immutability, locality, avoiding side-effects. Instead, we have this big, globally visible, mutable variable. Instead of referential transparency, we (the framework) have to do a little dance to set/unset this mutable variable everytime before/after we run the application code.

Despite this, this technique is probably the most common way of getting around the "pass it everywhere" problem. It's used to access the Session variables in the [Play 2.0 Java api](http://www.playframework.org/documentation/2.0.4/JavaSessionFlash), in the python world it's used in the [Flask Web Microframework](http://flask.pocoo.org/docs/reqcontext/) and [Pyramid Web Framework](http://pyramid.readthedocs.org/en/latest/narr/threadlocals.html) to access the request information. Scala's [DynamicVariable](http://www.scala-lang.org/api/current/scala/util/DynamicVariable.html) does essentially this.

This pattern works perfectly, as long as the control flow of your program is purely stack based. However, it breaks once you start working with more complex control flows, such as futures:

def receive = {
  case SomeMessage =>
    context.system.scheduleOnce(5 seconds) {
      sender ! DelayedResponse  // Danger!
    }
}

Now by the time the scheduled action ends up reading the value of `sender`, it may have been unset! Or it may have been set again by the next request that comes in, and we may see somebody else's context. This example was taken from [this post](http://helenaedelson.com/?p=879), which has a more detailed explanation of the dangers involved.

Naturally, it is possible to work around this by making your Executor do the proper set/unset dance before every scheduled task it runs for every Magic Global which is required. However, this means the Executor needs to know about every Magic Global that it needs to set/unset. Compare this to the Pass it Everywhere pattern above:

```scala
def receive(sender: Actor) = {
  case SomeMessage =>
    context.system.scheduleOnce(5 seconds) {
      sender ! DelayedResponse  // Danger!
    }
}
```

Despite it being more verbose and annoying to keep typing, this problem with scheduled tasks (and non stack-based control flow in general) does not exist, since `ctx` is automatically captured by the `Future` due to closure, like any other variable would be.

Nested `def`s and `class`s
--------------------------
This works by placing all the classes and functions which need the `Context` into a larger `def` or `class`, such that they can all share an outer scope which contains the `Context` but is not global.

```scala
// program.scala
class Outer(ctx: Context){
    class Thing(d: Double){ ... }
    class SecondThing(i: Int){ ... }
    class Third(s: String, d: Double){ ... }

    object Module{
        def doStuff(s: String){ ... }
        def getValues(i: Int) = ...
    }
}
```

Hence, you just need to instantiate a

```scala
val outer = new Outer(ctx)
```

and all the classes inside will be able to see its `Context` without having to pass it around. Furthermore, the `Context` is properly scoped to the `Outer`: you can have multiple `Outer`s existing at the same time without worrying about their `Context`s getting mixed up, or doing the set/unset dance like in the Magic Globals technique.

This seems ideal, except for one thing: a `class` cannot span more than one file. If our classes are large, and we want to keep our file sizes reasonable, we need to break this up into multiple files using Traits, the way it is done in the [Cake Pattern](http://stackoverflow.com/questions/5172188/understanding-scalas-cake-pattern):

```scala
// program.scala
class Outer(ctx: Context)
extends FirstPart
with SecondPart
with ThirdPart
with FourthPart{
    ...
}

// FirstPart.scala
trait FirstPart{ this: Outer =>
    class Thing(d: Double){ ... }
}

// SecondPart.scala
trait SecondPart{ this: Outer =>
    class SecondThing(i: Int){ ... }
}

// ThirdPart.scala
trait ThirdPart{ this: Outer =>
    class Third(s: String, d: Double){ ... }
}

// FourthPart.scala
trait FourthPart{ this: Outer =>
    object Module{
        def doStuff(s: String){ ... }
        def getValues(i: Int) = ...
    }
}
```

Which works. However, there is a huge amount of boilerplate: Every file needs to be chucked into a separate trait, who serves only to be composed together as a full class by Outer. This gives us a whole lot of flexiblity (we can inherit different sets of traits if we want, changing the classes we have available in Outer) which we do not want (we just want to not have to pass the damn `Context` around), which is almost the [definition of boilerplate](http://en.wikipedia.org/wiki/Boilerplate_code).

Furthermore, `Outer` needs the `extends FirstPart with SecondPart with ThirdPart with FourthPart`. Even with four separate files it is getting somewhat unwieldy, with larger projects it starts becoming annoying:

```scala
class Page(override val request: Request[AnyContent])
extends XPage
with BaseSlice
with AboutSlice
with BlogSlice
with HomeSlice
with LibSlice
with ProfileSlice
with RankingSlice
with StockSlice
with StatefulSlice{
    ...
}
```

or even

```scala
trait Analyzer extends AnyRef
with Contexts
with Namers
with Typers
with Infer
with Implicits
with Variances
with EtaExpansion
with SyntheticMethods
with Unapplies
with Macros
with NamesDefaults
with TypeDiagnostics
with ContextErrors
with StdAttachments
```

as the size of the project (and thus the number of files) increases. In comparison, Sinject allows you to write:

```scala
// program.scala
object Outer extends sinject.Module[Outer]
class Outer(ctx: Context){ ... }

// FirstPart.scala
import Outer.dynamic
class Thing(d: Double){ ... }

// SecondPart.scala
import Outer.dynamic
class SecondThing(i: Int){ ... }

// ThirdPart.scala
import Outer.dynamic
class Third(s: String, d: Double){ ... }

// FourthPart.scala
import Outer.dynamic
object Module{
    def doStuff(s: String){ ... }
    def getValues(i: Int) = ...
}
```

Replacing the annoying `trait FirstPart{ this: Outer =>` in every file which a much less conspicuous `import Outer.dynamic`, and saving the big chain of `with`s in the definition of `Outer`.

How it Works
============
Sinject uses a [Scala compiler plugin](http://www.scala-lang.org/node/140) which does most of its work before typechecking. Basically, it turns this

```scala
import Context.dynamic
class Thing(v: String, d: Double){
    ...
}


object Thing{
    def method = ...
    def doStuff(i: Int) = ...
}
```

into this:

```scala
class Thing(v: String, d: Double)(implicit ctx: Context){
    ...
}

object Thing{
    def method(implicit ctx: Context) = ...
    def doStuff(i: Int)(implicit ctx: Context) = ...
}
```

by looking for marker imports at the top of the file, in this case:

```scala
import Context.dynamic
```

After that, the auto-passing of context from class to class and method to method is handled entirely by the Scala compiler during typechecking, since the context just becomes another implicit.

The `Context()` function is simply shorthand for getting the implicit `Context` in scope, and behaves identically to `implicitly[Context]`.