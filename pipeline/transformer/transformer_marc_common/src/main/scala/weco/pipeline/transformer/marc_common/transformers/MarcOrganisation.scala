package weco.pipeline.transformer.marc_common.transformers

import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work.{AbstractAgent, Organisation}

trait MarcOrganisation extends MarcFieldTransformer with MarcAbstractAgent {

  override protected val ontologyType: String = "Organisation"
  override protected val appropriateFields: Seq[String] =
    Seq("110", "610", "710")
  override protected val labelSubfieldTags: Seq[String] = Seq(
    "a",
    "b",
    "c",
    "d",
    "t",
    "p",
    "q",
    "l"
  )

  override protected def createAgent(
    label: String,
    identifier: IdState.Unminted
  ): AbstractAgent[IdState.Unminted] =
    new Organisation(label = normaliseLabel(label), id = identifier)

}

object MarcOrganisation extends MarcOrganisation
