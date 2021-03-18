package uk.ac.wellcome.platform.ingestor.images

import uk.ac.wellcome.models.work.internal.ImageState
import weco.catalogue.internal_model.image.Image
import weco.catalogue.internal_model.image.ImageState.{Augmented, Indexed}

object ImageTransformer {
  val deriveData: Image[Augmented] => Image[Indexed] =
    image => image.transition[Indexed]()
}
