package weco.pipeline.transformer.marc_common.transformers.subjects

import weco.catalogue.internal_model.identifiers.IdState
import weco.pipeline.transformer.marc_common.transformers.MarcHasRecordControlNumber

trait DiscardMultipleIds {
  this: MarcHasRecordControlNumber =>
  override def handleMultipleIdFields(
    values: Seq[String],
    candidateIdentifier: IdState.Unminted
  ): IdState.Unminted = IdState.Unidentifiable

}
