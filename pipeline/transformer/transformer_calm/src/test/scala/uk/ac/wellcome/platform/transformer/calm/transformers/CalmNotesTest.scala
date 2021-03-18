package uk.ac.wellcome.platform.transformer.calm.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.work._
import weco.catalogue.source_model.generators.CalmRecordGenerators

class CalmNotesTest extends AnyFunSpec with Matchers with CalmRecordGenerators {
  it("extracts all the notes fields") {
    val record = createCalmRecordWith(
      ("AdminHistory", "Administered by the Active Administrator"),
      ("CustodHistory", "Collected by the Careful Custodian"),
      ("Acquisition", "Acquired by the Academic Archivists"),
      ("Appraisal", "Appraised by the Affable Appraiser"),
      ("Accruals", "Accrued by the Alliterative Acquirer"),
      ("RelatedMaterial", "Related to the Radiant Records"),
      ("PubInNote", "Published in the Public Pamphlet"),
      ("UserWrapped4", "Wrapped in the Worldly Words"),
      ("Copyright", "Copyright the Creative Consortium"),
      ("Arrangement", "Arranged in an Adorable Alignment")
    )

    val notes = CalmNotes(record, languageNote = None)

    // The ordering of the Notes field is arbitrary, because the underlying
    // Calm data is arbitrary.
    notes should contain theSameElementsAs List(
      BiographicalNote("Administered by the Active Administrator"),
      OwnershipNote("Collected by the Careful Custodian"),
      AcquisitionNote("Acquired by the Academic Archivists"),
      AppraisalNote("Appraised by the Affable Appraiser"),
      AccrualsNote("Accrued by the Alliterative Acquirer"),
      RelatedMaterial("Related to the Radiant Records"),
      PublicationsNote("Published in the Public Pamphlet"),
      FindingAids("Wrapped in the Worldly Words"),
      CopyrightNote("Copyright the Creative Consortium"),
      ArrangementNote("Arranged in an Adorable Alignment")
    )
  }

  it("includes the language note, if supplied") {
    val record = createCalmRecordWith(
      ("Arrangement", "Aligned Across the Axis"),
    )

    val languageNote = LanguageNote("Listed in Lampung and Lavongai")

    val notes = CalmNotes(record, languageNote = Some(languageNote))

    // The ordering of the Notes field is arbitrary, because the underlying
    // Calm data is arbitrary.
    notes should contain theSameElementsAs List(
      ArrangementNote("Aligned Across the Axis"),
      languageNote
    )
  }

  it("returns an empty list if none of the fields contain notes") {
    val record = createCalmRecordWith(
      "Title" -> "abc",
      "Level" -> "Collection",
      "RefNo" -> "a/b/c"
    )

    CalmNotes(record, languageNote = None) shouldBe empty
  }

  // This is a field that we used to transform, but Collections Information
  // have stopped using it.
  // See https://github.com/wellcomecollection/platform/issues/4773
  it("does not include the ReproductionConditions field") {
    val record = createCalmRecordWith(
      ("ReproductionConditions", "Reproduction is Rarely Regulated"),
    )

    CalmNotes(record, languageNote = None) shouldBe empty
  }
}
