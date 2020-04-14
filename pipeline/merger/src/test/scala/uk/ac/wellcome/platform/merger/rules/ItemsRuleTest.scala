package uk.ac.wellcome.platform.merger.rules

import org.scalatest.{FunSpec, Inside, Matchers}
import uk.ac.wellcome.models.work.generators.WorksGenerators
import uk.ac.wellcome.models.work.internal._
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

  it("Merges items from METS works into multi-item Sierra works") {
    inside(ItemsRule.merge(multiItemPhysicalSierra, List(metsWork))) {
      case FieldMergeResult(items, _) =>
        items should have size 3
        items should contain theSameElementsAs
          multiItemPhysicalSierra.data.items ++ metsWork.data.items
    }
  }

  it("Merges physical locations from Calm works into physical Sierra works") {
    inside(ItemsRule.merge(physicalSierra, List(calmWork))) {
      case FieldMergeResult(items, _) =>
        items should have size 1
        items.head.locations shouldBe List(
          calmWork.data.items.head.locations.head
        )
    }
  }

  it("Merges physical locations from Calm works into digital Sierra works") {
    inside(ItemsRule.merge(digitalSierra, List(calmWork))) {
      case FieldMergeResult(items, _) =>
        items should have size 1
        items.head.locations shouldBe List(
          calmWork.data.items.head.locations.head,
          digitalSierra.data.items.head.locations.head
        )
    }
  }

  it("Merges physical locations from Calm works into multi-item Sierra works") {
    inside(ItemsRule.merge(multiItemPhysicalSierra, List(calmWork))) {
      case FieldMergeResult(items, _) =>
        items shouldBe calmWork.data.items ++ multiItemPhysicalSierra.data.items
    }
  }
}
