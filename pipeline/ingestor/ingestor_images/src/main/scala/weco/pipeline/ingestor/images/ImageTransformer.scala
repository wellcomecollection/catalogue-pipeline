package weco.pipeline.ingestor.images

import weco.catalogue.display_model.image.DisplayImage
import weco.catalogue.internal_model.image.{Image, ImageState}
import weco.pipeline.ingestor.images.models.{
  DebugInformation,
  ImageAggregatableValues,
  ImageFilterableValues,
  ImageQueryableValues,
  ImageVectorValues,
  IndexedImage
}
import weco.catalogue.display_model.Implicits._
import io.circe.syntax._

import java.time.Instant

trait ImageTransformer {
  val deriveData: Image[ImageState.Augmented] => IndexedImage =
    image =>
      IndexedImage(
        modifiedTime = image.modifiedTime,
        display = DisplayImage(image).asJson.deepDropNullValues,
        query = ImageQueryableValues(image),
        aggregatableValues = ImageAggregatableValues(image),
        filterableValues = ImageFilterableValues(image),
        vectorValues = ImageVectorValues(image),
        debug = DebugInformation(indexedTime = getIndexedTime)
      )

  // This is a def rather than an inline call so we can override it in the
  // tests; in particular we want it to be deterministic when we're creating
  // example documents to send to the API repo.
  protected def getIndexedTime: Instant = Instant.now()
}

object ImageTransformer extends ImageTransformer
