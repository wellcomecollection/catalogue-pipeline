package uk.ac.wellcome.platform.merger.rules

import org.scalatest.{FunSpec, Inside, Matchers}
import uk.ac.wellcome.models.work.generators.WorksGenerators
import uk.ac.wellcome.platform.merger.models.FieldMergeResult

class ItemsRuleTest
    extends FunSpec
    with Matchers
    with WorksGenerators
    with Inside {
  val physicalSierra = createSierraPhysicalWork
  val multiItemPhysicalSierra = createSierraWorkWithTwoPhysicalItems
  val digitalSierra = createSierraDigitalWork
  val metsWork = createUnidentifiedInvisibleMetsWork
  val miroWork = createMiroWork
  val calmWork = createUnidentifiedCalmWork()
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

  // Miro
  it("merges locations from Miro items into single-item Sierra works") {
    inside(ItemsRule.merge(physicalSierra, List(miroWork))) {
      case FieldMergeResult(items, mergedSources) =>
        items should have size 1
        items.head.locations should contain theSameElementsAs
          physicalSierra.data.items.head.locations ++ miroWork.data.items.head.locations

        mergedSources should be(Seq(miroWork))
    }
  }

  it("doesn't merge Miro works into multi-item Sierra works") {
    inside(ItemsRule.merge(multiItemPhysicalSierra, List(miroWork))) {
      case FieldMergeResult(items, mergedSources) =>
        items should be(multiItemPhysicalSierra.data.items)
        mergedSources should be(Seq())
    }
  }

  // METS
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

  it("adds items from METS works into multi-item Sierra works") {
    inside(ItemsRule.merge(multiItemPhysicalSierra, List(metsWork))) {
      case FieldMergeResult(items, mergedSources) =>
        items should contain theSameElementsAs
          multiItemPhysicalSierra.data.items ++ metsWork.data.items
        mergedSources should be(Seq(metsWork))
    }
  }

  // Calm
  it(
    "take the PhysicalLocation from Calm work and replace single-item Sierra work item location") {
    inside(ItemsRule.merge(physicalSierra, List(calmWork))) {
      case FieldMergeResult(items, mergedSources) =>
        items should have size 1
        items.head.locations should have size 1
        items.head.locations.head should be(
          calmWork.data.items.head.locations.head)

        mergedSources should be(Seq(calmWork))
    }
  }

  it("adds items from Calm works into multi-item Sierra works") {
    inside(ItemsRule.merge(multiItemPhysicalSierra, List(calmWork))) {
      case FieldMergeResult(items, mergedSources) =>
        items should contain theSameElementsAs
          multiItemPhysicalSierra.data.items ++ calmWork.data.items
        mergedSources should be(Seq(calmWork))
    }
  }
}
