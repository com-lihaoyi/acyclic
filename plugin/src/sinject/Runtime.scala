package sinject

import annotation.StaticAnnotation


class Module[T] extends StaticAnnotation{
  def apply()(implicit m: T = throw NotInModuleError) = m
  def dynamic: T = ???

}

object NotInModuleError extends Error
