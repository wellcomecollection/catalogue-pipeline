package weco.pipeline.transformer.marc_common.transformers

import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work.Subject
import weco.pipeline.transformer.marc_common.models.MarcField

trait MarcSubject extends MarcFieldTransformer {
  type Output = List[Subject[IdState.Unminted]]
  def apply(field: MarcField)(
  ): Output = Nil
}
