package weco.pipeline.merger.models

import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.image.{ImageData, ImageSource}

case class ImageDataWithSource(
  imageData: ImageData[IdState.Identified],
  source: ImageSource
)
