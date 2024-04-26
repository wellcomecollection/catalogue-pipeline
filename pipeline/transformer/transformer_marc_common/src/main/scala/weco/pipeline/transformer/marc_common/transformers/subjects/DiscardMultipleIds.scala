package weco.pipeline.transformer.marc_common.transformers.subjects

import weco.catalogue.internal_model.identifiers.IdState
import weco.pipeline.transformer.marc_common.transformers.MarcHasRecordControlNumber

/** Given a field with multiple Marc Record Control Number subfields ignore them
  * all.
  *
  * This exists to preserve some existing inconsistent behaviour where some
  * fields generate a label derived id if they cannot reliably extract a proper
  * id, and others declare the field to be unidentifiable.
  *
  * Once a decision has been taken on the direction in which that inconsistency
  * should be resolved then this can be removed
  */
trait DiscardMultipleIds {
  this: MarcHasRecordControlNumber =>
  override def handleMultipleIdFields(
    values: Seq[String],
    candidateIdentifier: IdState.Unminted
  ): IdState.Unminted = IdState.Unidentifiable

}
