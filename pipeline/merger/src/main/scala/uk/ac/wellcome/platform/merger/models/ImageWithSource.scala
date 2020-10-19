package uk.ac.wellcome.platform.merger.models

import uk.ac.wellcome.models.work.internal.{
  DataState,
  ImageSource,
  UnmergedImage
}

case class ImageWithSource(
  image: UnmergedImage[DataState.Unidentified],
  source: ImageSource[DataState.Unidentified]
)
