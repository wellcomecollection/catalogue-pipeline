package uk.ac.wellcome.platform.transformer.calm.transformers

import uk.ac.wellcome.platform.transformer.calm.exceptions.TransformerException.FieldMissingTransformerException
import uk.ac.wellcome.platform.transformer.calm.models.CalmSourceData

object CalmTitle extends CalmFieldTransformer {
  type Output = String

  def transform(calmTransformable: CalmSourceData) = {
    calmTransformable.title match {
      case Some(title) => Right(title)
      case None        => Left(FieldMissingTransformerException)
    }
  }
}
