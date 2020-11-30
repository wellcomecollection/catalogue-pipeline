package uk.ac.wellcome.platform.merger.models

import uk.ac.wellcome.models.work.internal.{
  DataState,
  Image,
  ImageSource,
  ImageState
}

case class ImageWithSource(
  image: Image[ImageState.Source],
  source: ImageSource[DataState.Unidentified]
)
