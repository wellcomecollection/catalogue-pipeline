package uk.ac.wellcome.platform.transformer.calm.transformers

import uk.ac.wellcome.models.work.internal.Collection
import uk.ac.wellcome.platform.transformer.calm.models.CalmSourceData

object CalmCollection extends CalmFieldTransformer {
  type Output = Collection

  def transform(calmTransformable: CalmSourceData) = {
    Right(
      Collection(
        label = calmTransformable.altRefNo,
        path = calmTransformable.refNo))
  }
}
