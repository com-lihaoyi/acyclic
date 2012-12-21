Sinject
=======
**Sinject** is a Scala compiler plugin that performs [dependency injection](http://en.wikipedia.org/wiki/Dependency_injection). This saves you the hassle of constantly passing commonly-used parameters from method to method, allowing you to turn a mess like this:

    class Thing(d: Double)(implicit ctx: Context){ ... }
    class SecondThing(i: Int)(implicit ctx: Context){ ... }
    class Third(s: String, d: Double)(implicit ctx: Context){ ... }

    object Module{
        def doStuff(s: String)(implicit ctx: Context){ ... }
        def getValues(i: Int)(implicit ctx: Context) = ...
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
