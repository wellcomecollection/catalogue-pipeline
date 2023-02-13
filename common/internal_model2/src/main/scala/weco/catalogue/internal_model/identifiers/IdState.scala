package weco.catalogue.internal_model.identifiers

sealed trait IdState {
  def maybeCanonicalId: Option[CanonicalId]
  def allSourceIdentifiers: List[SourceIdentifier]
}

object IdState {

  sealed trait Unminted extends IdState

  sealed trait Minted extends IdState

  /** Represents an ID that has been successfully minted, and thus has a
    * canonicalId assigned.
    */
  case class Identified(
    canonicalId: CanonicalId,
    sourceIdentifier: SourceIdentifier,
    otherIdentifiers: List[SourceIdentifier] = Nil
  ) extends Minted {
    def maybeCanonicalId: Option[CanonicalId] = Some(canonicalId)
    def allSourceIdentifiers: List[SourceIdentifier] =
      sourceIdentifier +: otherIdentifiers
  }

  /** Represents an ID that has not yet been minted, but will have a canonicalId
    * assigned later in the pipeline.
    *
    * @param identifiedType
    *   \- this is used in the ID minter. What type will this become after it
    *   gets a canonical ID?
    */
  case class Identifiable(
    sourceIdentifier: SourceIdentifier,
    otherIdentifiers: List[SourceIdentifier] = Nil,
    identifiedType: String = classOf[Identified].getSimpleName
  ) extends Unminted {
    def maybeCanonicalId: Option[CanonicalId] = None
    def allSourceIdentifiers: List[SourceIdentifier] =
      sourceIdentifier +: otherIdentifiers
  }

  /** Represents an ID that has no sourceIdentifier and thus impossible to have
    * a canonicalId assigned. Note that it is possible for this ID to be either
    * pre or post minter.
    */
  case object Unidentifiable extends Unminted with Minted {
    def maybeCanonicalId: Option[CanonicalId] = None
    def allSourceIdentifiers: List[SourceIdentifier] = Nil
  }
}
