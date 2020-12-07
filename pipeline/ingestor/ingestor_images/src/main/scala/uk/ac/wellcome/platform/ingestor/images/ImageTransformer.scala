package uk.ac.wellcome.platform.ingestor.images

import uk.ac.wellcome.models.work.internal.{Image, ImageState}

object ImageTransformer {
  val deriveData: Image[ImageState.Augmented] => Image[ImageState.Indexed] =
    image => image.transition[ImageState.Indexed]()
}
