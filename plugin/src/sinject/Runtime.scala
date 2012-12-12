package sinject

import annotation.{implicitNotFound, StaticAnnotation}


class Module[T] extends StaticAnnotation{
  def apply()(implicit m: T = throw NotInModuleError) = m
  def dynamic: T = ???

}

object NotInModuleError extends Error("Cannot call Module-local variable from static context.")
