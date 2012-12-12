package sinject

import annotation.{implicitNotFound, StaticAnnotation}


class Module[T] extends StaticAnnotation{
  def apply()(implicit m: T = throw NotInModuleError) = m
  def dynamic = throw UsingDynamicError
}
object UsingDynamicError extends Error(
  "`dynamic` is just a marker name, you are not meant to actually *use* it!"
)
object NotInModuleError extends Error(
  "You need to be within the implicit-scope to access this."
)
