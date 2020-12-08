package uk.ac.wellcome.platform.merger.models

import uk.ac.wellcome.models.work.internal.{
  DataState,
  IdState,
  ImageData,
  ImageSource
}

case class ImageDataWithSource(
  imageData: ImageData[IdState.Identifiable],
  source: ImageSource[DataState.Unidentified]
)
