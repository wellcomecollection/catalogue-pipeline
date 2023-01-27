package weco.pipeline.transformer.miro.transformers

import weco.catalogue.internal_model.work.Format

trait MiroFormat {

  /** We set the same work type on all Miro images.
    *
    * This is based on the Sierra work types -- we'll want to revisit this when
    * we sort out work types properly, but it'll do for now.
    */
  def getFormat: Option[Format] =
    Some(Format.DigitalImages)
}
