package weco.pipeline.transformer.sierra.transformers.subjects

import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work.{AbstractRootConcept, Subject}
import weco.pipeline.transformer.marc_common.models.{MarcField, MarcRecord}
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
trait SierraSubjectsTransformer2
    extends SierraIdentifiedDataTransformer
    with SierraMarcDataConversions {
  private type SingleOutput = Subject[IdState.Unminted]
  protected type OptionalSingleOutput = Option[SingleOutput]
  override type Output = Seq[SingleOutput]

  protected def getSubject(field: MarcField): OptionalSingleOutput
  protected val subjectVarFields: Seq[String]
  def apply(bibId: SierraBibNumber, bibData: SierraBibData): Output =
    getSubjects(bibData)
  protected def getSubjects(marcData: MarcRecord): Output =
    marcData
      .fieldsWithTags(subjectVarFields: _*)
      .filterNot(_.indicator2.contains("7"))
      .flatMap(getSubject)
}
trait SierraSubjectsTransformer
    extends SierraIdentifiedDataTransformer
    with SierraAbstractConcepts
    with SierraQueryOps
    with SierraMarcDataConversions
    with SierraSubjectsTransformer2 {

  override protected val defaultSecondIndicator: String = "0"
  override protected def getSubjects(marcData: MarcRecord): Output =
    throw new Exception(
      "Don't!"
    )
  protected val subjectVarFields: List[String]
  protected val labelSubfields: Seq[String]
  protected val ontologyType: String
  override def apply(bibId: SierraBibNumber, bibData: SierraBibData): Output =
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

  private def getSubjectsFromVarFields(
    bibId: SierraBibNumber,
    varFields: List[VarField]
  ): Output = {
    // Second indicator 7 means that the subject authority is something other
    // than library of congress or mesh. Some MARC records have duplicated subjects
    // when the same subject has more than one authority (for example mesh and FAST),
    // which causes duplicated subjects to appear in the API.
    //
    // Example from b10199135 (j7jm24hj)
    //  650  2 Retina|xphysiology.|0D012160Q000502
    //  650  2 Vision, Ocular.|0D014785
    //  650  2 Visual Pathways.|0D014795
    //  650  7 Retina.|2fast|0(OCoLC)fst01096191
    //  650  7 Vision.|2fast|0(OCoLC)fst01167852
    //
    // So let's filter anything that is from another authority for now.
    varFields
      .filterNot(_.indicator2.contains("7"))
      .flatMap(getSubjectFromField(_))
  }

  protected def getIdState(field: MarcField): IdState.Unminted =
    super.getIdState(field, ontologyType)

  private def getSubjectFromField(
    field: MarcField
  ): Option[Subject[IdState.Unminted]] = {

    getSubjectConcepts(field) match {
      case Success(Nil) => None
      case Success(entities) =>
        Some(
          Subject(
            label = getLabel(field).get,
            concepts = entities.toList,
            id = getIdState(field)
          )
        )
      case Failure(exception) =>
        None
      // throw exception // TODO: was CataloguingException with context in Organisation, log and move on in others

    }
  }
}
