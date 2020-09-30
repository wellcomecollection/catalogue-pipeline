package uk.ac.wellcome.platform.merger.rules

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{Inside, Inspectors}
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.merger.generators.WorksWithImagesGenerators
import uk.ac.wellcome.platform.merger.models.FieldMergeResult

class OtherIdentifiersRuleTest
    extends AnyFunSpec
    with Matchers
    with WorksWithImagesGenerators
    with Inside
    with Inspectors {
  val nothingWork = createSourceWorkWith(
    sourceIdentifier = SourceIdentifier(
      identifierType = IdentifierType("fake", "fake"),
      value = "fake"
    ))
  val miroWork = createMiroWork
  val metsWorks = (0 to 3).map(_ => createInvisibleMetsSourceWork).toList
  val physicalSierra = createSierraSourceWorkWith(
    items = List(createPhysicalItem),
    format = Some(Format.Pictures)
  )
  val zeroItemPhysicalSierra = createSierraSourceWorkWith(
    items = Nil,
    format = Some(Format.Pictures)
  )
  val physicalMapsSierra = physicalSierra.copy(
    data = physicalSierra.data.copy(
      format = Some(Format.Maps)
    )
  )
  val sierraWorkWithTwoPhysicalItems = createSierraWorkWithTwoPhysicalItems
  val calmWork =
    createCalmSourceWork

  val mergeCandidate = createSierraSourceWork
  val sierraWithMergeCandidate = physicalSierra.copy(
    data = physicalSierra.data.copy(
      mergeCandidates = List(
        MergeCandidate(
          mergeCandidate.sourceIdentifier,
          Some("Physical/digitised Sierra work")))
    ))

  it("merges METS, Miro, and Sierra source IDs into Calm target") {
    inside(
      OtherIdentifiersRule
        .merge(
          calmWork,
          physicalSierra :: nothingWork :: miroWork :: metsWorks)) {
      case FieldMergeResult(otherIdentifiers, mergedSources) =>
        otherIdentifiers should contain theSameElementsAs
          List(physicalSierra.sourceIdentifier, miroWork.sourceIdentifier) ++
            metsWorks.map(_.sourceIdentifier) ++ calmWork.data.otherIdentifiers

        mergedSources should contain theSameElementsAs (physicalSierra :: miroWork :: metsWorks)
    }
  }

  it(
    "merges a Miro source ID into single-item Sierra work with METS and a single miro merge candidates") {
    inside(
      OtherIdentifiersRule
        .merge(physicalSierra, nothingWork :: miroWork :: metsWorks)) {
      case FieldMergeResult(otherIdentifiers, mergedSources) =>
        otherIdentifiers should contain theSameElementsAs
          miroWork.sourceIdentifier :: physicalSierra.data.otherIdentifiers

        mergedSources should contain only miroWork
    }
  }

  it("does not merge any Miro source IDs when there is more than 1 Miro work") {
    val miroWork2 = createMiroWork
    inside(
      OtherIdentifiersRule
        .merge(physicalSierra, List(nothingWork, miroWork, miroWork2))) {
      case FieldMergeResult(otherIdentifiers, mergedSources) =>
        otherIdentifiers should contain theSameElementsAs physicalSierra.data.otherIdentifiers
        mergedSources shouldBe empty
    }
  }

  it("merges Miro source IDs into a Sierra work with zero items") {
    inside(OtherIdentifiersRule.merge(zeroItemPhysicalSierra, List(miroWork))) {
      case FieldMergeResult(otherIdentifiers, mergedSources) =>
        otherIdentifiers should contain theSameElementsAs
          miroWork.sourceIdentifier :: zeroItemPhysicalSierra.data.otherIdentifiers

        mergedSources should contain theSameElementsAs List(miroWork)
    }
  }

  it(
    "does not merge any Miro source IDs into Sierra works with format != picture/digital image/3D object") {
    inside(
      OtherIdentifiersRule
        .merge(physicalMapsSierra, List(nothingWork, miroWork))) {
      case FieldMergeResult(otherIdentifiers, mergedSources) =>
        otherIdentifiers should contain theSameElementsAs physicalMapsSierra.data.otherIdentifiers
        mergedSources shouldBe empty
    }
  }

  it("appends a linked digitised Sierra work sourceIdentifiers") {
    inside(
      OtherIdentifiersRule
        .merge(
          sierraWithMergeCandidate,
          List(nothingWork, miroWork, mergeCandidate))) {
      case FieldMergeResult(otherIdentifiers, mergedSources) =>
        otherIdentifiers should contain theSameElementsAs
          miroWork.sourceIdentifier :: mergeCandidate.identifiers ++
            sierraWithMergeCandidate.data.otherIdentifiers

        mergedSources should contain theSameElementsAs List(
          miroWork,
          mergeCandidate)
    }
  }

  it("only merges miro source identifiers") {
    val miroWorkWithOtherSources = miroWork.copy(
      data = miroWork.data.copy(
        otherIdentifiers = List(
          SourceIdentifier(
            identifierType = IdentifierType("miro-library-reference"),
            ontologyType = "Work",
            value = randomAlphanumeric(32)
          ))
      ))
    inside(
      OtherIdentifiersRule
        .merge(physicalSierra, List(miroWorkWithOtherSources))) {
      case FieldMergeResult(otherIdentifiers, mergeCandidates) =>
        otherIdentifiers should contain theSameElementsAs
          (miroWork.sourceIdentifier :: physicalSierra.data.otherIdentifiers)

        mergeCandidates should contain theSameElementsAs List(
          miroWorkWithOtherSources)
    }
  }

  it("does not merge any METS IDs") {
    inside(OtherIdentifiersRule.merge(physicalSierra, metsWorks)) {
      case FieldMergeResult(otherIdentifiers, mergedSources) =>
        forAll(otherIdentifiers) { id =>
          id.identifierType.id should not be ("mets")
        }

        mergedSources shouldBe empty
    }
  }
}
