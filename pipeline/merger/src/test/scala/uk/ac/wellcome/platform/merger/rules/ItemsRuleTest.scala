package uk.ac.wellcome.platform.merger.rules

import org.scalatest.Inside
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.models.work.generators.WorksGenerators
import uk.ac.wellcome.platform.merger.models.FieldMergeResult

class ItemsRuleTest
    extends AnyFunSpec
    with Matchers
    with WorksGenerators
    with Inside {
  val physicalSierra = createSierraPhysicalWork
  val multiItemPhysicalSierra = createSierraWorkWithTwoPhysicalItems
  val digitalSierra = createSierraDigitalWork
  val metsWork = createUnidentifiedInvisibleMetsWork
  val miroWork = createMiroWork
  val calmWork = createUnidentifiedCalmWork
  val (sierraWorkWithMergeCandidate, sierraWorkMergeCandidate) =
    createSierraWorkWithDigitisedMergeCandidate

  it(
    "leaves items unchanged and returns a digitised version of a Sierra work as a merged source") {
    inside(
      ItemsRule
        .merge(sierraWorkWithMergeCandidate, List(sierraWorkMergeCandidate))) {
      case FieldMergeResult(items, mergedSources) =>
        items should have size 1
        items should be(sierraWorkWithMergeCandidate.data.items)

        mergedSources should be(Seq(sierraWorkMergeCandidate))
    }
  }

  // Sierra single item
  it("merges locations from Miro items into single-item Sierra works") {
    inside(ItemsRule.merge(physicalSierra, List(miroWork))) {
      case FieldMergeResult(items, mergedSources) =>
        items should have size 1
        items.head.locations should contain theSameElementsAs
          physicalSierra.data.items.head.locations ++ miroWork.data.items.head.locations

        mergedSources should be(Seq(miroWork))
    }
  }

  it("merges item locations in METS work into single-item Sierra works item") {
    inside(ItemsRule.merge(physicalSierra, List(metsWork))) {
      case FieldMergeResult(items, mergedSources) =>
        items should have size 1
        items.head.locations shouldBe
          physicalSierra.data.items.head.locations ++
            metsWork.data.items.head.locations

        mergedSources should be(Seq(metsWork))
    }
  }

  it(
    "override Miro merging with METS merging into single-item Sierra works item") {
    inside(ItemsRule.merge(physicalSierra, List(miroWork, metsWork))) {
      case FieldMergeResult(items, mergedSources) =>
        items should have size 1
        items.head.locations shouldBe
          physicalSierra.data.items.head.locations ++
            metsWork.data.items.head.locations

        mergedSources should contain theSameElementsAs (Seq(metsWork, miroWork))
    }
  }

  // Sierra multi items
  it("doesn't merge Miro works into multi-item Sierra works") {
    inside(ItemsRule.merge(multiItemPhysicalSierra, List(miroWork))) {
      case FieldMergeResult(items, mergedSources) =>
        items should be(multiItemPhysicalSierra.data.items)
        mergedSources should be(Seq())
    }
  }

  it("adds items from METS works into multi-item Sierra works") {
    inside(ItemsRule.merge(multiItemPhysicalSierra, List(metsWork))) {
      case FieldMergeResult(items, mergedSources) =>
        items should contain theSameElementsAs
          multiItemPhysicalSierra.data.items ++ metsWork.data.items
        mergedSources should be(Seq(metsWork))
    }
  }

  // Calm
  it("Adds Sierra item IDs to Calm item") {
    inside(ItemsRule.merge(calmWork, List(physicalSierra))) {
      case FieldMergeResult(items, mergedSources) =>
        items should have size 1
        items.head.id should be(physicalSierra.data.items.head.id)

        mergedSources should be(Seq(physicalSierra))
    }
  }

  it("Adds the METS item location to the Calm item") {
    inside(ItemsRule.merge(calmWork, List(metsWork))) {
      case FieldMergeResult(items, mergedSources) =>
        items should have size 1
        items.head.locations should contain theSameElementsAs (calmWork.data.items.head.locations ++ metsWork.data.items.head.locations)

        mergedSources should be(Seq(metsWork))
    }
  }

  it("Adds Sierra item IDs and the METS item location to a Calm work") {
    inside(ItemsRule.merge(calmWork, List(physicalSierra, metsWork))) {
      case FieldMergeResult(items, mergedSources) =>
        items should have size 1
        items.head.id should be(physicalSierra.data.items.head.id)
        items.head.locations should contain theSameElementsAs (calmWork.data.items.head.locations ++ metsWork.data.items.head.locations)

        mergedSources should contain theSameElementsAs Seq(
          metsWork,
          physicalSierra)
    }
  }
}
