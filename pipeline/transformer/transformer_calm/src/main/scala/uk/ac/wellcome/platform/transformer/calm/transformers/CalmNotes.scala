package uk.ac.wellcome.platform.transformer.calm.transformers

import uk.ac.wellcome.platform.transformer.calm.{CalmRecordOps, NormaliseText}
import weco.catalogue.internal_model.work._
import weco.catalogue.source_model.calm.CalmRecord

object CalmNotes extends CalmRecordOps {
  private val notesMapping = List(
    ("AdminHistory", BiographicalNote(_)),
    ("CustodHistory", OwnershipNote(_)),
    ("Acquisition", AcquisitionNote(_)),
    ("Appraisal", AppraisalNote(_)),
    ("Accruals", AccrualsNote(_)),
    ("RelatedMaterial", RelatedMaterial(_)),
    ("PubInNote", PublicationsNote(_)),
    ("UserWrapped4", FindingAids(_)),
    ("Copyright", CopyrightNote(_)),
    ("Arrangement", ArrangementNote(_))
  )

  def apply(record: CalmRecord,
            languageNote: Option[LanguageNote]): List[Note] =
    notesMapping.flatMap {
      case (key, createNote) =>
        record
          .getList(key)
          .map(NormaliseText(_))
          .map(createNote)
    } ++ List(languageNote).flatten
}
