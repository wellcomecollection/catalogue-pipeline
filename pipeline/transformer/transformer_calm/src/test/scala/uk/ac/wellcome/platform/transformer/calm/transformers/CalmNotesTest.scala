package uk.ac.wellcome.platform.transformer.calm.transformers

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
    )

    val notes = CalmNotes(record)

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
      ArrangementNote("Arranged in an Adorable Alignment"),
      LocationOfDuplicatesNote("A copy is contained in the Circular Church"),
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
