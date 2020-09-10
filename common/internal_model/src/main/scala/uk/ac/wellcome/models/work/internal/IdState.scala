package uk.ac.wellcome.models.work.internal

/** Represents an ID that is attached to individual pieces of work data.
  *  The ID can be in 3 possible states:
  *
  *  * Identifiable: contains a sourceIdentifier but does not have a canonicalId
  *    yet (i.e. pre minter)
  *  * Identified: contains a sourceIdentifier and a canonicalId (i.e. post
  *    minter)
  *  * Unidentifiable: a piece of data that does not have a sourceIdentifier, and
  *    thus can never have a canonicalId attached
  *  */
sealed trait IdState {
  def maybeCanonicalId: Option[String]
  def allSourceIdentifiers: List[SourceIdentifier]
}

object IdState {

  /* Parent trait for all IDs that contain a sourceIdentifier */
  sealed trait WithSourceIdentifier extends IdState

  /* Parent trait for an ID of an object that is pre minter. */
  sealed trait Unminted extends IdState

  /* Parent trait for an ID of an object that is post minter. */
  sealed trait Minted extends IdState

  /** Represents an ID that has been successfully minted, and thus has a
    *  canonicalId assigned. */
  case class Identified(
    canonicalId: String,
    sourceIdentifier: SourceIdentifier,
    otherIdentifiers: List[SourceIdentifier] = Nil,
  ) extends Minted
      with WithSourceIdentifier {
    def maybeCanonicalId = Some(canonicalId)
    def allSourceIdentifiers = sourceIdentifier +: otherIdentifiers
  }

  /** Represents an ID that has not yet been minted, but will have a canonicalId
    *  assigned later in the pipeline. */
  case class Identifiable(
    sourceIdentifier: SourceIdentifier,
    otherIdentifiers: List[SourceIdentifier] = Nil,
    identifiedType: String = classOf[Identified].getSimpleName,
  ) extends Unminted
      with WithSourceIdentifier {
    def maybeCanonicalId = None
    def allSourceIdentifiers = sourceIdentifier +: otherIdentifiers
  }

  /** Represents an ID that has no sourceIdentifier and thus impossible to have a
    *  canonicalId assigned. Note that it is possible for this ID to be either pre
    *  or post minter. */
  case object Unidentifiable extends Unminted with Minted {
    def maybeCanonicalId = None
    def allSourceIdentifiers = Nil
  }
}

/** A trait assigned to all objects that contain some ID value. */
trait HasId[+T] {
  val id: T
}
