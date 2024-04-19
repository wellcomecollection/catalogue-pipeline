package weco.pipeline.transformer.sierra.transformers.subjects

import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work.{AbstractRootConcept, Subject}
import weco.pipeline.transformer.marc_common.models.MarcField
import weco.pipeline.transformer.sierra.data.SierraMarcDataConversions
import weco.pipeline.transformer.sierra.transformers.{
  SierraAbstractConcepts,
  SierraIdentifiedDataTransformer
}
import weco.pipeline.transformer.text.TextNormalisation._
import weco.sierra.models.SierraQueryOps
import weco.sierra.models.data.SierraBibData
import weco.sierra.models.identifiers.SierraBibNumber
import weco.sierra.models.marc.VarField

import scala.util.{Failure, Success, Try}

trait SierraSubjectsTransformer
    extends SierraIdentifiedDataTransformer
    with SierraAbstractConcepts
    with SierraQueryOps
    with SierraMarcDataConversions {

  type Output = List[Subject[IdState.Unminted]]

  protected val subjectVarFields: List[String]
  protected val labelSubfields: Seq[String]
  protected val ontologyType: String
  def apply(bibId: SierraBibNumber, bibData: SierraBibData): Output =
    getSubjectsFromVarFields(
      bibId,
      subjectVarFields.flatMap(bibData.varfieldsWithTag(_))
    )

  override protected def getLabel(field: MarcField): Option[String] =
    Option(
      field.subfields
        .filter(subfield => labelSubfields.contains(subfield.tag))
        .map(_.content)
        .mkString(" ")
        .trimTrailingPeriod
    ).filter(_.nonEmpty)

  protected def getSubjectConcepts(
    field: MarcField
  ): Try[Seq[AbstractRootConcept[IdState.Unminted]]]

  def getSubjectsFromVarFields(
    bibId: SierraBibNumber,
    varFields: List[VarField]
  ): Output = {
    varFields.map(getSubjectFromField(_))
  }
  def getSubjectFromField(field: MarcField): Subject[IdState.Unminted] = {

    getSubjectConcepts(field) match {
      case Success(entities) =>
        Subject(
          label = getLabel(field).get,
          concepts = entities.toList,
          id = getIdState(field, ontologyType)
        )
      case Failure(exception) =>
        throw exception // TODO: was CataloguingException with context in Organisation, log and move on in others

    }
  }
}
