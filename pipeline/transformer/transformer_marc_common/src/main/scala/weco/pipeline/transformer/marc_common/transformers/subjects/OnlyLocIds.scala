package weco.pipeline.transformer.marc_common.transformers.subjects

import weco.catalogue.internal_model.identifiers.IdState
import weco.pipeline.transformer.marc_common.models.MarcField
import weco.pipeline.transformer.marc_common.transformers.MarcHasRecordControlNumber

trait OnlyLocIds extends MarcHasRecordControlNumber {

  /** This is probably unnecessary as I believe that the only reason for
    * excluding MeSH in Agent Subjects is that an Agent is unlikely to have a
    * valid MeSH ID.
    *
    * If that is the case, we might as well let it happen. There are some
    * Organisations with MeSH ids (e.g. WHO), but I don't know if they are used.
    *
    * The main reason for including this feature is to maintain the interface
    * specified by some existing tests during a refactor.
    */
  override def getIdState(
    field: MarcField,
    ontologyType: String
  ): IdState.Unminted = {
    getSecondIndicator(field) match {
      case "0" =>
        super.getIdState(
          field,
          ontologyType
        )
      case _ =>
        getLabelDerivedIdentifier(ontologyType, field)
    }
  }
}
