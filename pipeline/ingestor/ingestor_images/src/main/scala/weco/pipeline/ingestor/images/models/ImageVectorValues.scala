package weco.pipeline.ingestor.images.models

import weco.catalogue.internal_model.image.{Image, ImageState, InferredData}

case class ImageVectorValues(
  features1: List[Float],
  features2: List[Float],
  reducedFeatures: List[Float],
  paletteEmbedding: List[Float]
)

object ImageVectorValues {
  def apply(image: Image[ImageState.Augmented]): ImageVectorValues = {
    val inferredData = image.state.inferredData
    ImageVectorValues(
      features1 = inferredData.features1,
      features2 = inferredData.features2,
      reducedFeatures = inferredData.reducedFeatures,
      paletteEmbedding = inferredData.paletteEmbedding
    )
  }
}
