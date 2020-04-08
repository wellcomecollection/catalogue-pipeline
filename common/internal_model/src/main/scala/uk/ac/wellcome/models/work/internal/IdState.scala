package uk.ac.wellcome.models.work.internal

/** The root trait for all possible ID types. */
sealed trait IdState {
  def maybeCanonicalId: Option[String]
  def allSourceIdentifiers: List[SourceIdentifier]
}
sealed trait WithSourceIdentifier extends IdState
/** Parent trait for an ID of an object that is pre minter. */
sealed trait Unminted extends IdState

/** Parent trait for an ID of an object that is post minter. */
sealed trait Minted extends IdState

/** Represents an ID that has been successfully minted, and thus has a
  *  canonicalId assigned. */
case class Identified(
  canonicalId: String,
  sourceIdentifier: SourceIdentifier,
  otherIdentifiers: List[SourceIdentifier] = Nil,
) extends IdState
    with Minted with WithSourceIdentifier{
  def maybeCanonicalId = Some(canonicalId)
  def allSourceIdentifiers = sourceIdentifier +: otherIdentifiers
}

/** Represents an ID that has not yet been minted, but will have a canonicalId
  *  assigned later in the pipeline. */
case class Identifiable(
  sourceIdentifier: SourceIdentifier,
  otherIdentifiers: List[SourceIdentifier] = Nil,
  identifiedType: String = classOf[Identified].getSimpleName,
) extends IdState
    with Unminted with WithSourceIdentifier{
  def maybeCanonicalId = None
  def allSourceIdentifiers = sourceIdentifier +: otherIdentifiers
}

/** Represents an ID that has no sourceIdentifier and thus impossible to have a
  *  canonicalId assigned. Note that it is possible for this ID to be either pre
  *  or post minter. */
case object Unidentifiable extends IdState with Unminted with Minted {
  def maybeCanonicalId = None
  def allSourceIdentifiers = Nil
}

/** A trait assigned to all objects that contain some ID value. */
trait HasIdState[+Id] {
  val id: Id
}
