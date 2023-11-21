package weco.pipeline.ingestor.images.models

import weco.catalogue.internal_model.image.{ImageSource, ParentWork}

trait ImageValues {
  protected def fromParentWork[R](
    source: ImageSource
  )(transform: ParentWork => R): R = source match {
    case p: ParentWork => transform(p)
  }
}
