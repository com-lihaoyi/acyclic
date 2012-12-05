package sinject


class Module[T] {
  def apply(m: T = throw sinject.NotInModuleError) = m
}

object NotInModuleError extends Error