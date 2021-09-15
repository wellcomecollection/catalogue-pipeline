package weco.catalogue.internal_model.identifiers

import io.circe.{Decoder, Encoder}

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
  def maybeCanonicalId: Option[CanonicalId]
  def allSourceIdentifiers: List[SourceIdentifier]
}

object IdState {

  /* Parent trait for an ID of an object that is pre minter. */
  sealed trait Unminted extends IdState

  /* Parent trait for an ID of an object that is post minter. */
  sealed trait Minted extends IdState

  /** Represents an ID that has been successfully minted, and thus has a
    *  canonicalId assigned. */
  case class Identified(
    canonicalId: CanonicalId,
    sourceIdentifier: SourceIdentifier,
    otherIdentifiers: List[SourceIdentifier] = Nil,
  ) extends Minted {
    def maybeCanonicalId = Some(canonicalId)
    def allSourceIdentifiers = sourceIdentifier +: otherIdentifiers
  }

  case object Identified {
    implicit val encoder: Encoder[Identified] =
      Encoder.forProduct3[
        Identified,
        CanonicalId,
        SourceIdentifier,
        List[SourceIdentifier]](
        "canonicalId",
        "sourceIdentifier",
        "otherIdentifiers")((id: Identified) =>
        (id.canonicalId, id.sourceIdentifier, id.otherIdentifiers))

    implicit val decoder: Decoder[Identified] =
      Decoder.forProduct3[
        Identified,
        CanonicalId,
        SourceIdentifier,
        List[SourceIdentifier]](
        "canonicalId",
        "sourceIdentifier",
        "otherIdentifiers")((canonicalId, sourceIdentifier, otherIdentifiers) =>
        Identified(canonicalId, sourceIdentifier, otherIdentifiers))
  }

  /** Represents an ID that has not yet been minted, but will have a canonicalId
    *  assigned later in the pipeline. */
  case class Identifiable(
    sourceIdentifier: SourceIdentifier,
    otherIdentifiers: List[SourceIdentifier] = Nil,
    identifiedType: String = classOf[Identified].getSimpleName,
  ) extends Unminted {
    def maybeCanonicalId = None
    def allSourceIdentifiers = sourceIdentifier +: otherIdentifiers
  }

  case object Identifiable {
    implicit val encoder: Encoder[Identifiable] =
      Encoder.forProduct3[
        Identifiable,
        SourceIdentifier,
        List[SourceIdentifier],
        String]("sourceIdentifier", "otherIdentifiers", "identifiedType")(
        (id: Identifiable) =>
          (id.sourceIdentifier, id.otherIdentifiers, id.identifiedType))

    implicit val decoder: Decoder[Identifiable] =
      Decoder.forProduct3[
        Identifiable,
        SourceIdentifier,
        List[SourceIdentifier],
        String]("sourceIdentifier", "otherIdentifiers", "identifiedType")(
        (sourceIdentifier, otherIdentifiers, identifiedType) =>
          Identifiable(sourceIdentifier, otherIdentifiers, identifiedType))
  }

  /** Represents an ID that has no sourceIdentifier and thus impossible to have a
    *  canonicalId assigned. Note that it is possible for this ID to be either pre
    *  or post minter. */
  case object Unidentifiable extends Unminted with Minted {
    def maybeCanonicalId = None
    def allSourceIdentifiers = Nil
  }
}
