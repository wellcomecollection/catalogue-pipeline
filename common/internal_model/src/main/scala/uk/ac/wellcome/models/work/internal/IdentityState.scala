package uk.ac.wellcome.models.work.internal

sealed trait IdentityState[+T]

sealed trait MaybeDisplayable[+T] extends IdentityState[T] {
  val thing: T
  def withThing[A](f: T => A): MaybeDisplayable[A]
}

sealed trait Displayable[+T] extends IdentityState[T] {
  val thing: T
  def withThing[A](f: T => A): Displayable[A]
}

case class Identified[T](thing: T,
                         canonicalId: String,
                         sourceIdentifier: SourceIdentifier,
                         otherIdentifiers: List[SourceIdentifier] = List())
    extends IdentityState[T]
    with Displayable[T]
    with MultipleSourceIdentifiers {
  def withThing[A](f: T => A) = this.copy(thing = f(thing))
}

case class Identifiable[T](thing: T,
                           sourceIdentifier: SourceIdentifier,
                           otherIdentifiers: List[SourceIdentifier] = List(),
                           identifiedType: String =
                             classOf[Identified[T]].getSimpleName)
    extends IdentityState[T]
    with MaybeDisplayable[T]
    with MultipleSourceIdentifiers {
  def withThing[A](f: T => A) = this.copy(thing = f(thing))
}

case class Unidentifiable[T](thing: T)
    extends IdentityState[T]
    with Displayable[T]
    with MaybeDisplayable[T] {
  def withThing[A](f: T => A) = this.copy(thing = f(thing))
}
