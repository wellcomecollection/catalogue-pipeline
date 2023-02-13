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
  type TransitionArgs

  val canonicalId: CanonicalId
  val sourceIdentifier: SourceIdentifier

  def id: String = canonicalId.toString
}

/** ImageState represents the state of the image in the pipeline. Its stages are
  * as follows:
  *
  * \| \| (merger) ▼ Initial \| \| (inferrer) ▼ Augmented \| \| (ingestor) ▼
  * Indexed
  */
object ImageState {

  case class Initial(
    sourceIdentifier: SourceIdentifier,
    canonicalId: CanonicalId
  ) extends ImageState {
    type TransitionArgs = Unit
  }

  case class Augmented(
    sourceIdentifier: SourceIdentifier,
    canonicalId: CanonicalId,
    inferredData: InferredData
  ) extends ImageState {
    type TransitionArgs = InferredData
  }
}

case class InferredData(
  // We split the feature vector so that it can fit into
  // ES's dense vector type (max length 2048)
  features1: List[Float],
  features2: List[Float],
  reducedFeatures: List[Float],
  palette: List[String],
  averageColorHex: Option[String],
  binSizes: List[List[Int]],
  binMinima: List[Float],
  aspectRatio: Option[Float]
)

object InferredData {
  def empty: InferredData = InferredData(
    features1 = Nil,
    features2 = Nil,
    reducedFeatures = Nil,
    palette = Nil,
    averageColorHex = None,
    binSizes = Nil,
    binMinima = Nil,
    aspectRatio = None
  )
}
