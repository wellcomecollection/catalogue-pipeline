package uk.ac.wellcome.platform.merger.rules

import org.scalatest.{FunSpec, Inside, Inspectors, Matchers}
import uk.ac.wellcome.models.work.generators.WorksGenerators
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.merger.models.FieldMergeResult

class OtherIdentifiersRuleTest
    extends FunSpec
    with Matchers
    with WorksGenerators
    with Inside
    with Inspectors {
  val nothingWork = createUnidentifiedWorkWith(
    sourceIdentifier = SourceIdentifier(
      identifierType = IdentifierType("fake", "fake"),
      value = "fake"
    ))
  val miroWorks = (0 to 3).map(_ => createMiroWork).toList
  val metsWorks = (0 to 3).map(_ => createUnidentifiedInvisibleMetsWork).toList
  val physicalSierra = createSierraPhysicalWork
  val sierraWorkWithTwoPhysicalItems = createSierraWorkWithTwoPhysicalItems
  val calmWork =
    createUnidentifiedCalmWork

  val mergeCandidate = createUnidentifiedSierraWork
  val sierraWithMergeCandidate = physicalSierra.copy(data = physicalSierra.data.copy(
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
          physicalSierra :: nothingWork :: metsWorks ++ miroWorks)) {
      case FieldMergeResult(otherIdentifiers, mergedSources) =>
        otherIdentifiers should contain theSameElementsAs physicalSierra.sourceIdentifier :: miroWorks
          .map(_.sourceIdentifier) ++ metsWorks.map(_.sourceIdentifier) ++ calmWork.identifiers

        mergedSources should contain theSameElementsAs (physicalSierra :: metsWorks ++ miroWorks)
    }
  }

  it("merges Miro source IDs into Sierra work with single item with METS and miro merge candidates") {
    inside(
      OtherIdentifiersRule
        .merge(physicalSierra, nothingWork :: metsWorks ++ miroWorks)) {
      case FieldMergeResult(otherIdentifiers, mergedSources) =>
        otherIdentifiers should contain theSameElementsAs miroWorks
          .map(_.sourceIdentifier) ++ physicalSierra.identifiers

        mergedSources should contain theSameElementsAs (metsWorks ++ miroWorks)
    }
  }

  it("merges METS source IDs into Sierra work with multi items") {
    inside(
      OtherIdentifiersRule
        .merge(
          sierraWorkWithTwoPhysicalItems,
          nothingWork :: metsWorks ++ miroWorks)) {
      case FieldMergeResult(otherIdentifiers, mergedSources) =>
        otherIdentifiers should contain theSameElementsAs metsWorks.map(
          _.sourceIdentifier) ++ sierraWorkWithTwoPhysicalItems.identifiers

        mergedSources should contain theSameElementsAs (metsWorks)
    }
  }

  it("appends a linked digitised Sierra work sourceIdentifiers") {
    inside(
      OtherIdentifiersRule
        .merge(
          sierraWithMergeCandidate,
          nothingWork :: mergeCandidate :: miroWorks)) {
      case FieldMergeResult(otherIdentifiers, mergedSources) =>
        otherIdentifiers should contain theSameElementsAs mergeCandidate.sourceIdentifier :: sierraWithMergeCandidate.identifiers ++ miroWorks.map(
          _.sourceIdentifier)

        mergedSources should contain theSameElementsAs (mergeCandidate :: miroWorks)
    }
  }

  it("only merges miro source identifiers") {
    val miroWorksWithOtherSources = miroWorks.map(miroWork =>
      miroWork.copy(data = miroWork.data.copy(
        otherIdentifiers = List(SourceIdentifier(
          identifierType = IdentifierType("miro-library-reference"),
          ontologyType = "Work",
          value = randomAlphanumeric(32)
        ))
      ))
    )
    inside(OtherIdentifiersRule.merge(physicalSierra, miroWorksWithOtherSources)) {
      case FieldMergeResult(otherIdentifiers, mergeCandidates) =>
        otherIdentifiers should contain theSameElementsAs(physicalSierra.identifiers ++ miroWorks.map(_.sourceIdentifier))

        mergeCandidates should contain theSameElementsAs (miroWorksWithOtherSources)
    }
  }

  it("does not merge any METS IDs and have them as a merged source") {
    inside(OtherIdentifiersRule.merge(physicalSierra, metsWorks)) {
      case FieldMergeResult(otherIdentifiers, mergedSources) =>
        forAll(otherIdentifiers) { id =>
          id.identifierType.id should not be ("mets")
        }

      mergedSources should be (metsWorks)
    }
  }
}
