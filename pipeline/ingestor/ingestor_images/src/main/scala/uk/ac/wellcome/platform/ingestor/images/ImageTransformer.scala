package uk.ac.wellcome.platform.ingestor.images

import uk.ac.wellcome.models.work.internal.{Image, ImageState}
import ImageState.{Augmented, Indexed}

object ImageTransformer {
  val deriveData: Image[Augmented] => Image[Indexed] =
    image => image.transition[Indexed]()
}
