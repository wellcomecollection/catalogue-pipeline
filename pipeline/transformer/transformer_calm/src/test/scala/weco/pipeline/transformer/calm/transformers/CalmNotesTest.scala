package weco.pipeline.transformer.calm.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.work._
import weco.catalogue.source_model.generators.CalmRecordGenerators

class CalmNotesTest extends AnyFunSpec with Matchers with CalmRecordGenerators {
  it("extracts all the notes fields") {
    val record = createCalmRecordWith(
      ("AdminHistory", "Administered by the Active Administrator"),
      ("CustodialHistory", "Collected by the Careful Custodian"),
      ("Acquisition", "Acquired by the Academic Archivists"),
      ("Appraisal", "Appraised by the Affable Appraiser"),
      ("Accruals", "Accrued by the Alliterative Acquirer"),
      ("RelatedMaterial", "Related to the Radiant Records"),
      ("PubInNote", "Published in the Public Pamphlet"),
      ("UserWrapped4", "Wrapped in the Worldly Words"),
      ("Copyright", "Copyright the Creative Consortium"),
      ("Arrangement", "Arranged in an Adorable Alignment"),
      ("Copies", "A copy is contained in the Circular Church"),
      ("Notes", "Named now by Nicola Noble"),
      ("Originals", "The original object is in the Orange Octagon")
    )

    val notes = CalmNotes(record)

    // The ordering of the Notes field is arbitrary, because the underlying
    // Calm data is arbitrary.
    notes should contain theSameElementsAs List(
      Note(
        contents = "Administered by the Active Administrator",
        noteType = NoteType.BiographicalNote),
      Note(
        contents = "Collected by the Careful Custodian",
        noteType = NoteType.OwnershipNote),
      Note(
        contents = "Acquired by the Academic Archivists",
        noteType = NoteType.AcquisitionNote),
      Note(
        contents = "Appraised by the Affable Appraiser",
        noteType = NoteType.AppraisalNote),
      Note(
        contents = "Accrued by the Alliterative Acquirer",
        noteType = NoteType.AccrualsNote),
      Note(
        contents = "Related to the Radiant Records",
        noteType = NoteType.RelatedMaterial),
      Note(
        contents = "Published in the Public Pamphlet",
        noteType = NoteType.PublicationsNote),
      Note(
        contents = "Wrapped in the Worldly Words",
        noteType = NoteType.FindingAids),
      Note(
        contents = "Copyright the Creative Consortium",
        noteType = NoteType.CopyrightNote),
      Note(
        contents = "Arranged in an Adorable Alignment",
        noteType = NoteType.ArrangementNote),
      Note(
        contents = "A copy is contained in the Circular Church",
        noteType = NoteType.LocationOfDuplicatesNote),
      Note(
        contents = "Named now by Nicola Noble",
        noteType = NoteType.GeneralNote),
      Note(
        contents = "The original object is in the Orange Octagon",
        noteType = NoteType.LocationOfOriginalNote)
    )
  }

  it("returns an empty list if none of the fields contain notes") {
    val record = createCalmRecordWith(
      "Title" -> "abc",
      "Level" -> "Collection",
      "RefNo" -> "a/b/c"
    )

    CalmNotes(record) shouldBe empty
  }

  // This is a field that we used to transform, but Collections Information
  // have stopped using it.
  // See https://github.com/wellcomecollection/platform/issues/4773
  it("does not include the ReproductionConditions field") {
    val record = createCalmRecordWith(
      ("ReproductionConditions", "Reproduction is Rarely Regulated"),
    )

    CalmNotes(record) shouldBe empty
  }
}
