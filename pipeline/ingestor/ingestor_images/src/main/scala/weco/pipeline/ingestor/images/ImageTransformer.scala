package weco.pipeline.ingestor.images

import weco.catalogue.display_model.image.DisplayImage
import weco.catalogue.internal_model.image.{Image, ImageState}
import weco.pipeline.ingestor.images.models.{
  ImageAggregatableValues,
  ImageQueryableValues,
  IndexedImage
}
import weco.catalogue.display_model.Implicits._
import io.circe.syntax._

object ImageTransformer {
  val deriveData: Image[ImageState.Augmented] => IndexedImage =
    image =>
      IndexedImage(
        modifiedTime = image.modifiedTime,
        display = DisplayImage(image).asJson.deepDropNullValues,
        query = ImageQueryableValues(
          id = image.state.canonicalId,
          sourceIdentifier = image.state.sourceIdentifier,
          locations = image.locations,
          inferredData = image.state.inferredData,
          source = image.source
        ),
        aggregatableValues = ImageAggregatableValues(image.source)
    )
}
