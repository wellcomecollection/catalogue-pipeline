package weco.pipeline.transformer.marc_common.transformers
import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work.Subject
import weco.pipeline.transformer.marc_common.models.MarcRecord
import weco.pipeline.transformer.marc_common.transformers.subjects.{
  MarcConceptSubject,
  MarcMeetingSubject,
  MarcOrganisationSubject,
  MarcPersonSubject,
  MarcSubject
}

object MarcSubjects extends MarcDataTransformer {

  override type Output = Seq[Subject[IdState.Unminted]]

  private val transformers: Map[String, MarcSubject] = (
    Seq("650", "648", "651").map(marcTag => marcTag -> MarcConceptSubject)
      ++ Seq(
        "600" -> MarcPersonSubject,
        "610" -> MarcOrganisationSubject,
        "611" -> MarcMeetingSubject
      )
  ).toMap

  private lazy val marcTags = transformers.keys.toSeq
  override def apply(record: MarcRecord): Output = {
    record.fieldsWithTags(marcTags: _*).flatMap {
      field => transformers(field.marcTag)(field)
    }
  }
}
