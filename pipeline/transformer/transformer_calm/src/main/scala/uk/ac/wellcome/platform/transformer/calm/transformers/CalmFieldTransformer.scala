package uk.ac.wellcome.platform.transformer.calm.transformers

import uk.ac.wellcome.platform.transformer.calm.models.CalmSourceData
import uk.ac.wellcome.platform.transformer.calm.exceptions.TransformerException.FieldTransformerException

trait CalmFieldTransformer {
  type Output
  def transform(calmTransformable: CalmSourceData)
    : Either[FieldTransformerException.type, Output]
}
