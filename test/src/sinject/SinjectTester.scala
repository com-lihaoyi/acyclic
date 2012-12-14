package sinject

import org.scalatest._



class SinjectTester extends FreeSpec{
  import TestUtils._

  "simplest possible example" in {

    val first = make[success.simple.Prog](0: Integer, "fail")
    val second = make[success.simple.Prog](123: Integer, "cow")
    assert(first() === "Two! fail One! 0")
    assert(second() === "Two! cow One! 123")

  }
  "two-level folder/module nesting" in {
    val first = make[success.nestedpackage.Outer](1: Integer)
    val second = make[success.nestedpackage.Outer](5: Integer)
    assert(first() === "15")
    assert(second() === "27")
  }

  "class with multiple argument lists" in {
    val first = make[success.multiplearglists.Injected](3: Integer)
    val second = make[success.multiplearglists.Injected](5: Integer)
    assert(first() === "3 | two 6 | three args 3 1.5 | 1.5 f 12 List(three, args) -128 3")
    assert(second() === "5 | two 8 | three args 5 1.5 | 1.5 f 12 List(three, args) -128 5")
  }

  "class nested in class" in {
    val first = make[success.nestedclass.Prog](1: Integer, "mooo")
    val second = make[success.nestedclass.Prog](5: Integer, "cow")
    assert(first() === "Inner! c 11mooo")
    assert(second() === "Inner! c 15cow")
  }

  "classes with existing implicits" in {
    val first = make[success.existingimplicit.Injected](1: Integer, "mooo", 1.5: java.lang.Double)
    val second = make[success.existingimplicit.Injected](5: Integer, "cow", 0.9: java.lang.Double)
    assert(first() === "mooo 1 | mooo 1 1.5 | mooo 1 1 | mooo 1 1 1.5 | mooo 1 1 1.5 64 c 10")
    assert(second() === "cow 5 | cow 5 0.9 | cow 5 5 | cow 5 5 0.9 | cow 5 5 0.9 64 c 10")
  }

  "classes with traits" in {
    val first = make[success.traits.Prog](1: Integer, 2: Integer)
    val second = make[success.traits.Prog](5: Integer, 6: Integer)
    assert(first() === "cow 1 2 | dog 1 2 4")
    assert(second() === "cow 5 6 | dog 5 6 12")
  }
  "classes with inheritence" in {
    val first = make[success.inheritence.Prog](1: Integer, "first")
    val second = make[success.inheritence.Prog](5: Integer, "second")
    assert(first() === "Self! 1 Parent! 2 : first first3 first4 1")
    assert(second() === "Self! 5 Parent! 10 : second second7 second4 5")
  }

  "dynamic scope in an objects methods" in {
    val first = make[success.objectmethods.Prog](1: Integer, "first")
    val second = make[success.objectmethods.Prog](5: Integer, "second")
    assert(first() === "first first2 first3 first1")
    assert(second() === "second second2 second3 second5")
  }

  "anonymous inner classes and traits" in {
    val first = make[success.anonymous.Prog](1: Integer, 2: Integer)
    val second = make[success.anonymous.Prog](5: Integer, 6: Integer)
    assert(first() === "first first2 first3 first1")
    assert(second() === "second second2 second3 second5")
  }
}


