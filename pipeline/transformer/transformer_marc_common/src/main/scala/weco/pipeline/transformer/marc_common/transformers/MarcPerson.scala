package weco.pipeline.transformer.marc_common.transformers

import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work.{AbstractAgent, Person}
import weco.pipeline.transformer.marc_common.models.MarcField

trait MarcPerson extends MarcFieldTransformer with MarcAbstractAgent {

  override protected val ontologyType: String = "Person"
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
  override protected def getLabel(field: MarcField): Option[String] = {
    super.getLabel(field) match {
      case None => None
      case Some(label) =>
        field.marcTag match {
          // Person labels should not be normalised for subjects
          case "600" => Some(label)
          // Person labels should be normalised for contributors
          case _ => Some(normaliseLabel(normaliseLabel(label)))
        }
    }
  }

  override protected def createAgent(
    label: String,
    identifier: IdState.Unminted
  ): AbstractAgent[IdState.Unminted] =
    new Person(label = label, id = identifier)

}
object MarcPerson extends MarcPerson
