package weco.pipeline.transformer.marc_common.transformers.subjects

import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work.{AbstractRootConcept, Subject}
import weco.pipeline.transformer.marc_common.models.MarcField
import weco.pipeline.transformer.marc_common.transformers.{
  MarcFieldTransformer,
  MarcHasRecordControlNumber
}
import weco.pipeline.transformer.text.TextNormalisation.TextNormalisationOps

import scala.util.{Failure, Success, Try}

trait MarcSubject extends MarcFieldTransformer with MarcHasRecordControlNumber {
  type Output = Option[Subject[IdState.Unminted]]

  protected val ontologyType: String
  protected val labelSubfields: Seq[String]

  protected def getSubjectConcepts(
    field: MarcField
  ): Try[Seq[AbstractRootConcept[IdState.Unminted]]]

  protected def getIdState(field: MarcField): IdState.Unminted =
    super.getIdState(field, ontologyType)

  def getSubject(field: MarcField)(
  ): Output = getSubjectConcepts(field) match {
    case Success(Nil) => None
    case Success(entities) =>
      getLabel(field).map(
        label =>
          Subject(
            label = label,
            concepts = entities.toList,
            id = getIdState(field)
          )
      )
    case Failure(exception) =>
      None
    // throw exception // TODO: was CataloguingException with context in Organisation, log and move on in others
  }

  override protected def getLabel(field: MarcField): Option[String] =
    Option(
      field.subfields
        .filter(subfield => labelSubfields.contains(subfield.tag))
        .map(_.content)
        .mkString(" ")
        .trimTrailingPeriod
    ).filter(_.nonEmpty)
}
