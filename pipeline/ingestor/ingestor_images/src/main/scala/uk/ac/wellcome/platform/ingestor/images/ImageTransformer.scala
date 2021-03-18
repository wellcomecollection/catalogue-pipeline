package uk.ac.wellcome.platform.ingestor.images

import weco.catalogue.internal_model.image.Image
import weco.catalogue.internal_model.image.ImageState.{Augmented, Indexed}

object ImageTransformer {
  val deriveData: Image[Augmented] => Image[Indexed] =
    image => image.transition[Indexed]()
}
