package uk.ac.wellcome.models.work.internal

sealed trait IdentityState[+T]

sealed trait Unminted[+T] extends IdentityState[T] {
  val agent: T
  def withAgent[A](f: T => A): Unminted[A]
}

sealed trait Minted[+T] extends IdentityState[T] {
  val agent: T
  def withAgent[A](f: T => A): Minted[A]
}

case class Identified[T](agent: T,
                         canonicalId: String,
                         sourceIdentifier: SourceIdentifier,
                         otherIdentifiers: List[SourceIdentifier] = List())
    extends IdentityState[T]
    with Minted[T]
    with MultipleSourceIdentifiers {
  def withAgent[A](f: T => A) = this.copy(agent = f(agent))
}

case class Identifiable[T](agent: T,
                           sourceIdentifier: SourceIdentifier,
                           otherIdentifiers: List[SourceIdentifier] = List(),
                           identifiedType: String =
                             classOf[Identified[T]].getSimpleName)
    extends IdentityState[T]
    with Unminted[T]
    with MultipleSourceIdentifiers {
  def withAgent[A](f: T => A) = this.copy(agent = f(agent))
}

case class Unidentifiable[T](agent: T)
    extends IdentityState[T]
    with Minted[T]
    with Unminted[T] {
  def withAgent[A](f: T => A) = this.copy(agent = f(agent))
}
