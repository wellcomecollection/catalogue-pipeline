package uk.ac.wellcome.platform.api.generators

import weco.catalogue.internal_model.generators.ImageGenerators
import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.image.{Image, ImageData, ImageState, InferredData}
import weco.catalogue.internal_model.work.{Work, WorkState}

trait ApiImageGenerators extends ImageGenerators {
  implicit class IdentifiableImageDataOps(imageData: ImageData[IdState.Identifiable]) {
    def toIndexedImageWith(
      canonicalId: String = createCanonicalId,
      parentWork: Work[WorkState.Identified] = identifiedWork(),
      redirectedWork: Option[Work[WorkState.Identified]] = None,
      inferredData: Option[InferredData] = createInferredData)
    : Image[ImageState.Indexed] =
      imageData
        .toIdentified
        .toAugmentedImageWith(
          inferredData = inferredData,
          parentWork = parentWork,
          redirectedWork = redirectedWork
        )
        .transition[ImageState.Indexed]()
  }
}
