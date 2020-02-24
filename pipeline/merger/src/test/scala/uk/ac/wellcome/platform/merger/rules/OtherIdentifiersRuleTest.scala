package uk.ac.wellcome.platform.merger.rules

import org.scalatest.{FunSpec, Inside, Inspectors, Matchers}
import uk.ac.wellcome.models.work.generators.WorksGenerators
import uk.ac.wellcome.models.work.internal.IdentifierType

class OtherIdentifiersRuleTest
    extends FunSpec
    with Matchers
    with WorksGenerators
    with Inside
    with Inspectors {
  val miroWorks = (0 to 3).map(_ => createMiroWork).toList
  val metsWorks = (0 to 3).map(_ => createUnidentifiedInvisibleMetsWork).toList
  val physicalSierra = createSierraPhysicalWork
  val digitalSierra = createSierraDigitalWork

  it("merges all Miro identifiers into the Sierra work") {
    inside(OtherIdentifiersRule.merge(physicalSierra, miroWorks)) {
      case MergeResult(otherIdentifiers, _) =>
        otherIdentifiers should contain theSameElementsAs miroWorks.flatMap(
          _.identifiers) ++ physicalSierra.otherIdentifiers
    }
  }

  it("does not merge Sierra identifiers from the Miro works") {
    val miroLibraryReferenceSourceIdentifier =
      createSourceIdentifierWith(IdentifierType("miro-library-reference"))
    val miroOtherIdentifiers = List(
      miroLibraryReferenceSourceIdentifier,
      physicalSierra.sourceIdentifier,
      createSierraSystemSourceIdentifier,
      createSierraIdentifierSourceIdentifier)
    val taintedMiroWork = miroWorks.head.copy(
      data = miroWorks.head.data.copy(otherIdentifiers = miroOtherIdentifiers)
    )
    inside(OtherIdentifiersRule.merge(digitalSierra, List(taintedMiroWork))) {
      case MergeResult(otherIdentifiers, _) =>
        otherIdentifiers should contain theSameElementsAs
          digitalSierra.otherIdentifiers ++ miroWorks.head.identifiers :+ miroLibraryReferenceSourceIdentifier
    }
  }

  it("merges identifiers from physical and digital Sierra works") {
    inside(OtherIdentifiersRule.merge(physicalSierra, List(digitalSierra))) {
      case MergeResult(otherIdentifiers, _) =>
        otherIdentifiers should contain theSameElementsAs
          physicalSierra.otherIdentifiers ++ digitalSierra.identifiers
    }
  }

  it("merges both physical/digital Sierra works and Miro works at once") {
    inside(
      OtherIdentifiersRule.merge(physicalSierra, miroWorks :+ digitalSierra)) {
      case MergeResult(otherIdentifiers, _) =>
        otherIdentifiers should contain theSameElementsAs miroWorks.flatMap(
          _.identifiers) ++ physicalSierra.otherIdentifiers ++ digitalSierra.identifiers
    }
  }

  it("does not merge any METS IDs into otherIdentifiers") {
    inside(OtherIdentifiersRule.merge(physicalSierra, metsWorks ++ miroWorks)) {
      case MergeResult(otherIdentifiers, _) =>
        forAll(otherIdentifiers) { id =>
          id.identifierType.id should not be ("mets")
        }
    }
  }
}
