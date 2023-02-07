package weco.pipeline.transformer.calm.transformers

import weco.catalogue.internal_model.work._
import weco.catalogue.source_model.calm.CalmRecord
import weco.pipeline.transformer.calm.NormaliseText
import weco.pipeline.transformer.calm.models.CalmRecordOps

object CalmNotes extends CalmRecordOps {
  private val noteTypeMapping = List(
    ("AdminHistory", NoteType.BiographicalNote),
    ("CustodialHistory", NoteType.OwnershipNote),
    ("Acquisition", NoteType.AcquisitionNote),
    ("Appraisal", NoteType.AppraisalNote),
    ("Accruals", NoteType.AccrualsNote),
    ("RelatedMaterial", NoteType.RelatedMaterial),
    ("PubInNote", NoteType.PublicationsNote),
    ("UserWrapped4", NoteType.FindingAids),
    ("Copyright", NoteType.CopyrightNote),
    ("Arrangement", NoteType.ArrangementNote),
    ("Copies", NoteType.LocationOfDuplicatesNote),
    ("Notes", NoteType.GeneralNote),
    ("Originals", NoteType.LocationOfOriginalNote)
  )

  def apply(record: CalmRecord): List[Note] =
    noteTypeMapping.flatMap {
      case (key, noteType) =>
        record
          .getList(key)
          .map(NormaliseText(_))
          .map(contents => Note(contents = contents, noteType = noteType))
    }
}
