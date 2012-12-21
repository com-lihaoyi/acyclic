Sinject
=======
**Sinject** is a Scala compiler plugin that performs [dependency injection](http://en.wikipedia.org/wiki/Dependency_injection). This saves you the hassle of constantly passing commonly-used parameters from class to class and method to method, allowing you to turn a mess like this:

    class Thing(d: Double)(ctx: Context){ ... new SecondThing(10)(ctx) ... }
    class SecondThing(i: Int)(ctx: Context){ ... new Third("lol", 1.5)(ctx) ... }
    class Third(s: String, d: Double)(ctx: Context){ ... doStuff(s)(ctx) ... }

    object Module{
        def doStuff(s: String)(ctx: Context){ ... getValues(s.length)(ctx) ... }
        def getValues(i: Int)(ctx: Context) = ... /* actually use the Context */ ...
    }

into 

    import Context.dynamic

    class Thing(d: Double){ ... }
    class SecondThing(i: Int){ ... }
    class Third(s: String, d: Double){ ... }

    object Module{
        def doStuff(s: String){ ... }
        def getValues(i: Int) = ...
    }

Removing a whole lot of pollution from the signatures of your `class`s and `def`s, keeping your code [DRY](http://en.wikipedia.org/wiki/Don%27t_repeat_yourself).

Setup
=====


How to Use
==========
First, you need to define your `Context` class with a companion object extending `sinject.Module[T]`:
    
    //context.scala 
    object Context extends sinject.Module[Context]
    class Context{
        val thing = new Thing("lol", 2)
        val value = "rofl"
    }

The `Context` class can be any old Scala class: there are no special requirements except that its companion must extend `sinject.Module[T]`. 

Now, in the file you want to inject the `Context` into, you place the marker import at the top of the file:
 
    //injected.scala
    import Context.dynamic 

    class Thing(v: String, i: Int){
        def print{
            println(v * i + Context().value)
        }
    }

And when you do a:

    new Context().thing.print

You will get

    lollolrofl    

As you can see, `print` could grab the value "rofl" from the surrounding `Context` without you needing to add it to the parameter list explicitly. In fact, now that we are injecting `Context` into `Thing`, a `Thing` can only be instantiated within a `Context`. So if you try to do this

    val thing = new Thing("lol", 2)

when not inside a `Context`, it will fail with a compile error.

When you only have a single `Thing`, 

    import Context.dynamic
    class Entity(){ ... }

is not much shorter than simply including the implicit directly:
  
    class Thing()(implicit ctx: Context){ ... }

And the two are basically equivalent: you can use your `Context` inside the `Thing`, it is automatically passed in (if you have an implicit `Context` in scope), you can't create a `Thing` without a `Context`, etc.

However, when you have a larger number of classes which all need this implicit `Context` to be passed in:

    class Thing()(implicit ctx: Context){ ... }
    class Person(name: String)(implicit ctx: Context){ ... }
    class Cow(weight: Double)(implicit ctx: Context){ ... }
    class Car(mileage: Double, capacity: Int)(implicit ctx: Context){ ... }

It is much more elegant to use Sinject to inject the implicits automatically:

    import Context.dynamic

    class Thing(){ ... }
    class Person(name: String){ ... }
    class Cow(weight: Double){ ... }
    class Car(mileage: Double, capacity: Int){ ... }

That way you can keep your code DRY and avoid having the annoying implicit parameter list cluttering up all your class signatures.

More
====

Transitivity
------------
The injection of contexts is transitive: if I create a class which gets the `Context` injected, and that class creates another class or calls a method which had the `Context` injected, the `Context` would be injected into them aswell:

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

would also print

    lollolrofl    

Since `Second` got the context injected into it when it was created inside the `Thing`

`def`s
----
Sinject also injects the context into the method `def`s of top level `object`s. e.g.

    import Context.dynamic
    object Cow{
        def moo{ 
            println(Context().value)
        }
    }

using the same `Context` defined earlier, would print

    rofl

`trait`s
--------
If a trait has a `Context` injected into it, it can only be mixed into a class which also has the context mixed into it. e.g.

    import Context.dynamic
    trait MyTrait{ ... }

can be mixed into

    import Context.dynamic
    class MyClass extends MyTrait{ ... } // this works

but cannot be mixed into

    class NonInjectedClass extends MyTrait{ ... } // this doesn't work

Within the body of the trait, the current `Context` can be accessed as normal, via `Context()`.

Multiple Injections
-------------------
It's possible to have larger contexts which get injected into smaller contexts, which are themselves injected into the individual classes. For example:

    // outer.scala
    object Outer extends sinject.Module[Outer]
    class Outer{ ... }

    // inner.scala
    import Outer.dynamic
    object Inner extends sinject.Module[Inner]
    class Inner{ ... }

    // myclass.scala
    import Inner.dynamic
    import Outer.dynamic
    class MyClass {
       ...
    }

As you would expect, an `Inner` can only be created inside the body of an `Outer`, and a `MyClass` can only be created within the body of an `Inner`. Inside `Inner`, you have `Outer()` to access the current `Outer`, and inside `MyClass`, you have both `Inner()` and `Outer()`, which would behave as you expect.

Limits
======
Sinject, in general, "just works" when passing context information around: It passes it from class to class through the constructor, from method to method through the parameter lists. It should "just work" for classes nested however deep, objects inside classes, anonymous classes, traits, classes inheriting from other classes, classes and methods with all kinds of parameter lists (None, Single, Multiple, with and without implicits).

However, there are still some rough edges:

static `val`s
-------------
Essentially the only place where the context cannot be passed is to static `val`s, which are defined on static `object`s. This is by design, as since these `val`s are evaluated just once, at load time, it is impossible to set it up such that different `context`s will see different versions of the `val`. Hence this

    import Context.dynamic
    object Thing{
        val x = Context().value + "lol"
    }

will fail at compile time, saying it cannot find the an implicit `Context` in scope. Only static `object` `val`s have this problem: `val`s inside to `class`s, `trait`s or `def`s, or these nested every which way, should all work fine.

Why
===
There are several alternatives to this, Sinject, and I will go through them one by one to show why they are not satisfactory. The basic problem is to cut down on the plumbing needed to pass variables from scope to scope:

    class Thing(d: Double)(ctx: Context){ ... new SecondThing(10)(ctx) ... }
    class SecondThing(i: Int)(ctx: Context){ ... new Third("lol", 1.5)(ctx) ... }
    class Third(s: String, d: Double)(ctx: Context){ ... doStuff(s)(ctx) ... }

    object Module{
        def doStuff(s: String)(ctx: Context){ ... getValues(s.length)(ctx) ... }
        def getValues(i: Int)(ctx: Context) = ... /* actually use the Context */ ...
    }

In this example, you can see that only `getValues` actually uses the `Context`. This is a pattern that comes up a lot, where your `Context` could be:

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
This is the approach taken by the Play! framework, as can be seen in [these](http://stackoverflow.com/questions/9751752/play-2-0-access-to-request-in-templates) [answers](http://stackoverflow.com/questions/9629250/how-to-avoid-passing-parameters-everywhere-in-play2] on stackoverflow. This is basically the problem I mentioned above.

This is pretty painful and results in the code I showed you above. The pain can be mitigated slightly by using implicits:

    class Thing(d: Double)(implicit ctx: Context){ ... new SecondThing(10) ... }
    class SecondThing(i: Int)(implicit ctx: Context){ ... new Third("lol", 1.5) ... }
    class Third(s: String, d: Double)(implicit ctx: Context){ ... doStuff(s) ... }

    object Module{
        def doStuff(s: String)(implicit ctx: Context){ ... getValues(s.length) ... }
        def getValues(i: Int)(implicit ctx: Context) = ... /* actually use the Context */ ...
    }

But only slightly: You no longer need to pass it in at every call site, but your method/class signatures get correspondingly longer, so it's debatable whether or not it's actually a "win".

Magic Globals!
--------------
Another popular approach is to use global (thread-local) variables that always seem to magically have the value you need, when you need them. An example is the [Lift S variable](http://exploring.liftweb.net/master/index-9.html#sub:Advanced-S-Object), an object that magically always has your current Session when your code is executed.

What is actually happening is something like this

    // request comes in
    S = ... // set the S object
    runYourCode()
    S = null // unset the S object

Every time before the framework starts running your code, it will set the S object to the current Session, so all your code can see is the current Session. It then unsets it, and re-sets it to the new context when the next request comes in.

This goes against essentially all the things we learn about structured programming: immutability, locality, avoiding side-effects. Instead, we have this big, globally visible, mutable variable. Instead of referential transparency, we (the framework) have to do a little dance to set/unset this mutable variable everytime before/after we run the application code.

Despite this, this technique is probably the most common way of getting around the "pass it everywhere" problem. It's used to access the Session variables in the [Play 2.0 Java api](http://www.playframework.org/documentation/2.0.4/JavaSessionFlash), in the python world it's used in the [Flask Web Microframework](http://flask.pocoo.org/docs/reqcontext/) and [Pyramid Web Framework](http://pyramid.readthedocs.org/en/latest/narr/threadlocals.html) to access the request information.

How it Works
============
Sinject basically turns this.

    import Context.dynamic 
    class Entity(v: String, d: Double){
        ...
    }

    object Thing{
        def method = ...
        def doStuff(i: Int) = ...
    }

into this:

    class Entity(v: String, d: Double)(implicit ctx: Context){
        ...
    }

    object Thing{
        def method(implicit ctx: Context) = ...
        def doStuff(i: Int)(implicit ctx: Context) = ...
    }

by looking for marker imports at the top of the file, in this case:

    import Context.dynamic
