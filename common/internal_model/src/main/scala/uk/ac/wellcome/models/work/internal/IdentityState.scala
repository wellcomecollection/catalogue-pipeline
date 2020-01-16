package uk.ac.wellcome.models.work.internal

sealed trait IdState {
  def id: Option[String]
  def otherIds: List[SourceIdentifier]
}

sealed trait Unminted extends IdState

sealed trait Minted extends IdState

case class Identified(
  canonicalId: String,
  sourceIdentifier: SourceIdentifier,
  otherIdentifiers: List[SourceIdentifier] = Nil,
) extends IdState
    with Minted {
  def id = Some(canonicalId)
  def otherIds = sourceIdentifier +: otherIdentifiers
}

case class Identifiable(
  sourceIdentifier: SourceIdentifier,
  otherIdentifiers: List[SourceIdentifier] = Nil,
  identifiedType: String = classOf[Identified].getSimpleName,
) extends IdState
    with Unminted {
  def id = None
  def otherIds = sourceIdentifier +: otherIdentifiers
}

case object Unidentifiable extends IdState with Unminted with Minted {
  def id = None
  def otherIds = Nil
}

trait HasIdState[+Id] {
  val id: Id
}
