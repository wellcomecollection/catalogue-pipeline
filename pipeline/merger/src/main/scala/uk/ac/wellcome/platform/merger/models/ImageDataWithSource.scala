package uk.ac.wellcome.platform.merger.models

import uk.ac.wellcome.models.work.internal.IdState
import weco.catalogue.internal_model.image.{ImageData, ImageSource}

case class ImageDataWithSource(
  imageData: ImageData[IdState.Identified],
  source: ImageSource
)
