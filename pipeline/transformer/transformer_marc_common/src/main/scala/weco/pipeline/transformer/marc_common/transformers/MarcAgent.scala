package weco.pipeline.transformer.marc_common.transformers

import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work.{AbstractAgent, Agent}

object MarcAgent extends MarcFieldTransformer with MarcAbstractAgent {

  override protected val ontologyType: String = "Agent"
  override protected val appropriateFields: Seq[String] =
    Seq("100", "600", "700")
  override protected val labelSubfieldTags: Seq[String] = Seq(
    "a",
    "b",
    "c",
    "d",
    "t",
    "n",
    "p",
    "q",
    "l"
  )

  override protected def createAgent(
    label: String,
    identifier: IdState.Unminted
  ): AbstractAgent[IdState.Unminted] =
    new Agent(label = label, id = identifier)
}
