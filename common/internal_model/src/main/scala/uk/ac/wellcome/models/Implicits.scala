package uk.ac.wellcome.models

import io.circe.generic.semiauto._
import io.circe._

import scala.collection.immutable.::

import uk.ac.wellcome.models.work.internal.{
  TransformedBaseWork,
  IdentifiedBaseWork,
  UnidentifiedWork,
  UnidentifiedInvisibleWork,
  UnidentifiedRedirectedWork,
  IdentifiedWork,
  BaseWork,
  InstantRange,
  Period,
  ProductionEvent,
  Person,
  Contributor,
  Identifiable,
  Unidentifiable,
  Item,
  Genre,
  DigitalLocation,
  PhysicalLocation,
  Location,
  AbstractRootConcept,
  MaybeDisplayable,
  AbstractAgent,
  AbstractConcept,
  SourceIdentifier,
  MergeCandidate,
  WorkType,
  Displayable,
}
import uk.ac.wellcome.models.matcher.MatcherResult

import uk.ac.wellcome.json.JsonUtil._

object Implicits {

  // Cache these here to improve compilation times (otherwise they are
  // re-derived every time they are required)

  implicit val _dec01: Decoder[Option[String]] = deriveDecoder
  implicit val _dec02: Decoder[SourceIdentifier] = deriveDecoder
  implicit val _dec03: Decoder[Identifiable[AbstractConcept]] = deriveDecoder
  implicit val _dec04: Decoder[Unidentifiable[AbstractConcept]] = deriveDecoder
  implicit val _dec05: Decoder[Person] = deriveDecoder
  implicit val _dec06: Decoder[MaybeDisplayable[AbstractConcept]] = deriveDecoder
  implicit val _dec07: Decoder[MaybeDisplayable[AbstractRootConcept]] = deriveDecoder
  implicit val _dec08: Decoder[MaybeDisplayable[AbstractAgent]] = deriveDecoder
  implicit val _dec09: Decoder[::[MaybeDisplayable[AbstractRootConcept]]] = deriveDecoder
  implicit val _dec10: Decoder[::[MaybeDisplayable[AbstractAgent]]] = deriveDecoder
  implicit val _dec11: Decoder[InstantRange] = deriveDecoder
  implicit val _dec12: Decoder[Period] = deriveDecoder
  implicit val _dec13: Decoder[DigitalLocation] = deriveDecoder
  implicit val _dec14: Decoder[PhysicalLocation] = deriveDecoder
  implicit val _dec15: Decoder[Location] = deriveDecoder
  implicit val _dec16: Decoder[Item] = deriveDecoder
  implicit val _dec17: Decoder[Genre[MaybeDisplayable[AbstractConcept]]] = deriveDecoder
  implicit val _dec18: Decoder[Contributor[MaybeDisplayable[AbstractAgent]]] = deriveDecoder
  implicit val _dec19: Decoder[ProductionEvent[MaybeDisplayable[AbstractAgent]]] = deriveDecoder
  implicit val _dec20: Decoder[MergeCandidate] = deriveDecoder
  implicit val _dec21: Decoder[WorkType] = deriveDecoder
  implicit val _dec22: Decoder[MatcherResult] = deriveDecoder
  implicit val _dec23: Decoder[Displayable[Item]] = deriveDecoder

  implicit val transformedBaseWorkDecoder: Decoder[TransformedBaseWork] = deriveDecoder
  implicit val identifiedBaseWorkDecoder: Decoder[IdentifiedBaseWork] = deriveDecoder
  implicit val unidentifiedWorkDecoder: Decoder[UnidentifiedWork] = deriveDecoder
  implicit val unidentifiedInvisibleWorkDecoder: Decoder[UnidentifiedInvisibleWork] = deriveDecoder
  implicit val identifiedWorkDecoder: Decoder[IdentifiedWork] = deriveDecoder
  implicit val unidentifiedRedirectedWorkDecoder: Decoder[UnidentifiedRedirectedWork] = deriveDecoder
  implicit val baseWorkDecoder: Decoder[BaseWork] = deriveDecoder

  implicit val _enc01: Encoder[Option[String]] = deriveEncoder
  implicit val _enc02: Encoder[SourceIdentifier] = deriveEncoder
  implicit val _enc03: Encoder[Identifiable[AbstractConcept]] = deriveEncoder
  implicit val _enc04: Encoder[Unidentifiable[AbstractConcept]] = deriveEncoder
  implicit val _enc05: Encoder[Person] = deriveEncoder
  implicit val _enc06: Encoder[MaybeDisplayable[AbstractConcept]] = deriveEncoder
  implicit val _enc07: Encoder[MaybeDisplayable[AbstractRootConcept]] = deriveEncoder
  implicit val _enc08: Encoder[MaybeDisplayable[AbstractAgent]] = deriveEncoder
  implicit val _enc09: Encoder[::[MaybeDisplayable[AbstractRootConcept]]] = deriveEncoder
  implicit val _enc10: Encoder[::[MaybeDisplayable[AbstractAgent]]] = deriveEncoder
  implicit val _enc11: Encoder[InstantRange] = deriveEncoder
  implicit val _enc12: Encoder[Period] = deriveEncoder
  implicit val _enc13: Encoder[DigitalLocation] = deriveEncoder
  implicit val _enc14: Encoder[PhysicalLocation] = deriveEncoder
  implicit val _enc15: Encoder[Location] = deriveEncoder
  implicit val _enc16: Encoder[Item] = deriveEncoder
  implicit val _enc17: Encoder[Genre[MaybeDisplayable[AbstractConcept]]] = deriveEncoder
  implicit val _enc18: Encoder[Contributor[MaybeDisplayable[AbstractAgent]]] = deriveEncoder
  implicit val _enc19: Encoder[ProductionEvent[MaybeDisplayable[AbstractAgent]]] = deriveEncoder
  implicit val _enc20: Encoder[MergeCandidate] = deriveEncoder
  implicit val _enc21: Encoder[WorkType] = deriveEncoder
  implicit val _enc22: Encoder[MatcherResult] = deriveEncoder
  implicit val _enc23: Encoder[Displayable[Item]] = deriveEncoder

  implicit val transformedBaseWorkEncoder: Encoder[TransformedBaseWork] = deriveEncoder
  implicit val identifiedBaseWorkEncoder: Encoder[IdentifiedBaseWork] = deriveEncoder
  implicit val unidentifiedWorkEncoder: Encoder[UnidentifiedWork] = deriveEncoder
  implicit val unidentifiedInvisibleWorkEncoder: Encoder[UnidentifiedInvisibleWork] = deriveEncoder
  implicit val identifiedWorkEncoder: Encoder[IdentifiedWork] = deriveEncoder
  implicit val unidentifiedRedirectedWorkEncoder: Encoder[UnidentifiedRedirectedWork] = deriveEncoder
  implicit val baseWorkEncoder: Encoder[BaseWork] = deriveEncoder
}
