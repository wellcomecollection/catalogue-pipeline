package weco.pipeline.transformer.sierra.transformers.subjects

import weco.catalogue.internal_model.identifiers.IdentifierType
import weco.pipeline.transformer.marc_common.transformers.MarcHasRecordControlNumber

trait ExcludeMeshIds extends MarcHasRecordControlNumber {

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
  override protected def getIdentifierType(
    indicator2: String,
    identifierValue: String
  ): Option[IdentifierType] = {
    indicator2 match {
      case "0" =>
        super.getIdentifierType(
          indicator2,
          identifierValue
        )
      case _ => None

    }
  }
}
