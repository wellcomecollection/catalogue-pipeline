package weco.pipeline.transformer.sierra.transformers.subjects

import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work.Subject
import weco.pipeline.transformer.marc_common.models.{MarcField, MarcRecord}
import weco.pipeline.transformer.sierra.data.SierraMarcDataConversions
import weco.pipeline.transformer.sierra.transformers.SierraIdentifiedDataTransformer
import weco.sierra.models.data.SierraBibData
import weco.sierra.models.identifiers.SierraBibNumber

trait SierraSubjectsTransformer
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
