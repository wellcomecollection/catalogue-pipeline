package weco.pipeline.transformer.marc_common.transformers

import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work.{AbstractAgent, Meeting}

object MarcMeeting extends MarcFieldTransformer with MarcAbstractAgent {

  override protected val ontologyType: String = "Meeting"
  override protected val appropriateFields: Seq[String] =
    Seq("111", "611", "711")
  override protected val labelSubfieldTags: Seq[String] = Seq(
    "a",
    "c",
    "d",
    "t"
  )

  override protected def createAgent(
    label: String,
    identifier: IdState.Unminted
  ): AbstractAgent[IdState.Unminted] =
    new Meeting(label = normaliseLabel(label), id = identifier)

}
