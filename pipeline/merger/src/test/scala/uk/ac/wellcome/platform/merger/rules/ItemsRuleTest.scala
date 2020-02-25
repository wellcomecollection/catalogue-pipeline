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

  it(
    "merges locations from digital Sierra items into single-item physical Sierra works") {
    inside(ItemsRule.merge(physicalSierra, List(digitalSierra))) {
      case FieldMergeResult(items, _) =>
        items should have size 1
        items.head.locations should contain theSameElementsAs
          physicalSierra.data.items.head.locations ++ digitalSierra.data.items.head.locations
    }
  }

  it(
    "merges items from digital Sierra works into multi-item physical Sierra works") {
    inside(ItemsRule.merge(multiItemPhysicalSierra, List(digitalSierra))) {
      case FieldMergeResult(items, _) =>
        items should have size 3
        items should contain theSameElementsAs
          multiItemPhysicalSierra.data.items ++ digitalSierra.data.items
    }
  }

  it("merges locations from Miro items into single-item Sierra works") {
    inside(ItemsRule.merge(physicalSierra, List(miroWork))) {
      case FieldMergeResult(items, _) =>
        items should have size 1
        items.head.locations should contain theSameElementsAs
          physicalSierra.data.items.head.locations ++ miroWork.data.items.head.locations
    }
  }

  it(
    "merges non-duplicate-URL locations from METS items into single-item Sierra works") {
    inside(ItemsRule.merge(physicalSierra, List(metsWork))) {
      case FieldMergeResult(items, _) =>
        items should have size 1
        items.head.locations should contain theSameElementsAs
          physicalSierra.data.items.head.locations ++ metsWork.data.items.head.locations
    }
  }

  it("Merges items from METS works into multi-item Sierra works") {
    inside(ItemsRule.merge(multiItemPhysicalSierra, List(metsWork))) {
      case FieldMergeResult(items, _) =>
        items should have size 3
        items should contain theSameElementsAs
          multiItemPhysicalSierra.data.items ++ metsWork.data.items
    }
  }

}
