package acyclic.plugin

import acyclic.file

import scala.collection.{SortedSet, SortedSetLike}
import scala.collection.mutable.Builder
import scala.collection.generic.{CanBuildFrom, SortedSetFactory}
import scala.language.implicitConversions

object Compat {

  // from https://github.com/scala/scala-collection-compat/blob/746a7de28223812b19d0d9f68d2253e0c5f655ca/compat/src/main/scala-2.11_2.12/scala/collection/compat/CompatImpl.scala#L8-L11
  private def simpleCBF[A, C](f: => Builder[A, C]): CanBuildFrom[Any, A, C] = new CanBuildFrom[Any, A, C] {
    def apply(from: Any): Builder[A, C] = apply()
    def apply(): Builder[A, C]          = f
  }

  // from https://github.com/scala/scala-collection-compat/blob/746a7de28223812b19d0d9f68d2253e0c5f655ca/compat/src/main/scala-2.11_2.12/scala/collection/compat/PackageShared.scala#L46-L49
  implicit def sortedSetCompanionToCBF[A: Ordering,
                                       CC[X] <: SortedSet[X] with SortedSetLike[X, CC[X]]](
      fact: SortedSetFactory[CC]): CanBuildFrom[Any, A, CC[A]] =
    simpleCBF(fact.newBuilder[A])

}