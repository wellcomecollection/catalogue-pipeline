package uk.ac.wellcome.models.work.internal

sealed trait IdState {
  def maybeCanonicalId: Option[String]
  def allSourceIdentifiers: List[SourceIdentifier]
}

sealed trait Unminted extends IdState

sealed trait Minted extends IdState

case class Identified(
  canonicalId: String,
  sourceIdentifier: SourceIdentifier,
  otherIdentifiers: List[SourceIdentifier] = Nil,
) extends IdState
    with Minted {
  def maybeCanonicalId = Some(canonicalId)
  def allSourceIdentifiers = sourceIdentifier +: otherIdentifiers
}

case class Identifiable(
  sourceIdentifier: SourceIdentifier,
  otherIdentifiers: List[SourceIdentifier] = Nil,
  identifiedType: String = classOf[Identified].getSimpleName,
) extends IdState
    with Unminted {
  def maybeCanonicalId = None
  def allSourceIdentifiers = sourceIdentifier +: otherIdentifiers
}

case object Unidentifiable extends IdState with Unminted with Minted {
  def maybeCanonicalId = None
  def allSourceIdentifiers = Nil
}

trait HasIdState[+Id] {
  val id: Id
}
