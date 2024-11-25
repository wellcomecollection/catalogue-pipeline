package weco.pipeline.ingestor.images.models

import weco.catalogue.internal_model.image.{Image, ImageState}

case class ImageVectorValues(
  features: List[Float],
  paletteEmbedding: List[Float]
)

object ImageVectorValues {
  def apply(image: Image[ImageState.Augmented]): ImageVectorValues = {
    val inferredData = image.state.inferredData
    ImageVectorValues(
      features = inferredData.features,
      paletteEmbedding = inferredData.paletteEmbedding
    )
  }
}
