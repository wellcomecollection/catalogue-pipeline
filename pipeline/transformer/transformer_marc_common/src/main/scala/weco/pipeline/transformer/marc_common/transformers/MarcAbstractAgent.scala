package weco.pipeline.transformer.marc_common.transformers

import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work.AbstractAgent
import weco.pipeline.transformer.marc_common.models.MarcField
import weco.pipeline.transformer.text.TextNormalisation.TextNormalisationOps

import scala.util.{Failure, Success, Try}

trait MarcAbstractAgent extends MarcHasRecordControlNumber {
  type Output = Try[AbstractAgent[IdState.Unminted]]
  // TODO: this is a Sierra quirk
  override protected val defaultSecondIndicator: String = "0"

  protected val ontologyType: String
  protected val appropriateFields: Seq[String]
  protected val labelSubfieldTags: Seq[String]
  protected def createAgent(
    label: String,
    identifier: IdState.Unminted
  ): AbstractAgent[IdState.Unminted]

  /** Construct a label from the subfields representing an agent.
    */
  protected def getLabel(field: MarcField): Option[String] = {
    val contents =
      field.subfields
        .filter {
          s => labelSubfieldTags.contains(s.tag)
        }
        .filterNot { _.content.trim.isEmpty }
        .map { _.content }

    contents match {
      case Nil          => None
      case nonEmptyList => Some(nonEmptyList mkString " ")
    }
  }

  protected def normaliseLabel(label: String): String = label.trimTrailing(',')

  private def isAppropriateField(field: MarcField): Boolean =
    appropriateFields.contains(field.marcTag)

  def apply(
    field: MarcField
  ): Try[AbstractAgent[IdState.Unminted]] = {
    if (isAppropriateField(field)) {
      getLabel(field) match {
        case Some(label) =>
          Success(
            createAgent(
              label,
              getIdState(field, ontologyType)
            )
          )
        case None =>
          Failure(
            new Exception(
              s"no label found when transforming $field into an $ontologyType"
            )
          )
      }
    } else {
      Failure(
        new Exception(
          s"attempt to transform incompatible MARC field ${field.marcTag} into $ontologyType"
        )
      )
    }
  }
}
