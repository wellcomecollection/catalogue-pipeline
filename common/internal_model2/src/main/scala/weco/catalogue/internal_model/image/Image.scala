package weco.catalogue.internal_model.image

import weco.catalogue.internal_model.identifiers.{
  CanonicalId,
  HasId,
  SourceIdentifier
}
import weco.catalogue.internal_model.locations.DigitalLocation

import java.time.Instant

case class ImageData[+State](
  id: State,
  version: Int,
  locations: List[DigitalLocation]
) extends HasId[State]

case class Image[State <: ImageState](
  version: Int,
  state: State,
  locations: List[DigitalLocation],
  modifiedTime: Instant
) {
  def id: String = state.id
  def sourceIdentifier: SourceIdentifier = state.sourceIdentifier
}

sealed trait ImageState {
  val canonicalId: CanonicalId
  val sourceIdentifier: SourceIdentifier

  def id: String = canonicalId.toString
}

object ImageState {

  case class Initial(
    sourceIdentifier: SourceIdentifier,
    canonicalId: CanonicalId
  ) extends ImageState
}
