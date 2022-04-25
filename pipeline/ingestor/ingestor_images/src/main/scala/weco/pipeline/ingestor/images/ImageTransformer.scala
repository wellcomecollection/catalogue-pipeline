package weco.pipeline.ingestor.images

import weco.catalogue.display_model.image.DisplayImage
import weco.catalogue.internal_model.image.Image
import weco.catalogue.internal_model.image.ImageState.{Augmented, Indexed}
import weco.pipeline.ingestor.images.models.IndexedImage
import weco.catalogue.display_model.Implicits._
import io.circe.syntax._

object ImageTransformer {
  val deriveData: Image[Augmented] => IndexedImage =
    image => {
      val indexedImage = image.transition[Indexed]()

      IndexedImage(
        version = indexedImage.version,
        state = indexedImage.state,
        locations = indexedImage.locations,
        source = indexedImage.source,
        modifiedTime = indexedImage.modifiedTime,
        display = DisplayImage(indexedImage).asJson.dropNullValues
      )
    }
}
