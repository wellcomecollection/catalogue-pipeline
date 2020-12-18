package uk.ac.wellcome.platform.merger.models

import uk.ac.wellcome.models.work.internal.{
  IdState,
  ImageData,
  ImageSource
}

case class ImageDataWithSource(
  imageData: ImageData[IdState.Identified],
  source: ImageSource
)
