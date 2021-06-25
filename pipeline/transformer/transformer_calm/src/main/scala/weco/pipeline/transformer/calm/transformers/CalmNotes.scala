package weco.pipeline.transformer.calm.transformers

import weco.catalogue.internal_model.work._
import weco.catalogue.source_model.calm.CalmRecord
import weco.pipeline.transformer.calm.NormaliseText
import weco.pipeline.transformer.calm.models.CalmRecordOps

object CalmNotes extends CalmRecordOps {
  private val notesMapping = List(
    ("AdminHistory", BiographicalNote(_)),
    ("CustodialHistory", OwnershipNote(_)),
    ("Acquisition", AcquisitionNote(_)),
    ("Appraisal", AppraisalNote(_)),
    ("Accruals", AccrualsNote(_)),
    ("RelatedMaterial", RelatedMaterial(_)),
    ("PubInNote", PublicationsNote(_)),
    ("UserWrapped4", FindingAids(_)),
    ("Copyright", CopyrightNote(_)),
    ("Arrangement", ArrangementNote(_)),
    ("Copies", LocationOfDuplicatesNote(_)),
  )

  def apply(record: CalmRecord): List[Note] =
    notesMapping.flatMap {
      case (key, createNote) =>
        record
          .getList(key)
          .map(NormaliseText(_))
          .map(createNote)
    }
}
