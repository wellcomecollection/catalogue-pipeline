package uk.ac.wellcome.platform.transformer.calm.transformers

import uk.ac.wellcome.models.work.internal.SourceIdentifier
import uk.ac.wellcome.platform.transformer.calm.models.{
  CalmIdentifier,
  CalmSourceData
}

object CalmSourceIdentifier extends CalmFieldTransformer {
  type Output = SourceIdentifier

  def transform(calmTransformable: CalmSourceData) = {
    Right(
      CalmIdentifier.recordId(calmTransformable.recordId)
    )
  }
}
