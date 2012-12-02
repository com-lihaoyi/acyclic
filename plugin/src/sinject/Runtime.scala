package sinject


class Module[T] {
  def apply()(implicit m: T = throw sinject.NotInModuleError) = m
}

object NotInModuleError extends Error