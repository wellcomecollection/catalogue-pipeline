package uk.ac.wellcome.platform.merger.rules

import org.scalatest.{FunSpec, Inside, Matchers}
import uk.ac.wellcome.models.work.generators.WorksGenerators
import uk.ac.wellcome.models.work.internal.{DigitalLocation, License}

class ItemsRuleTest
    extends FunSpec
    with Matchers
    with WorksGenerators
    with Inside {
  val physicalSierra = createSierraPhysicalWork
  val multiItemPhysicalSierra = createSierraWorkWithTwoPhysicalItems
  val digitalSierra = createSierraDigitalWork
  val metsWork = createUnidentifiedInvisibleMetsWork
  val metsWorkWithSierraUrl = createUnidentifiedInvisibleMetsWorkWith(
    items = List(
      createDigitalItemWith(
        locations = List(
          createDigitalLocationWith(
            url = digitalSierra.data.items.head.locations.head
              .asInstanceOf[DigitalLocation]
              .url,
            license = Some(License.InCopyright)
          ))
      )
    )
  )
  val miroWork = createMiroWork

  it(
    "merges locations from digital Sierra items into single-item physical Sierra works") {
    inside(ItemsRule.merge(physicalSierra, List(digitalSierra))) {
      case MergeResult(items, _) =>
        items should have size 1
        items.head.locations should contain theSameElementsAs
          physicalSierra.data.items.head.locations ++ digitalSierra.data.items.head.locations
    }
  }

  it(
    "merges items from digital Sierra works into multi-item physical Sierra works") {
    inside(ItemsRule.merge(multiItemPhysicalSierra, List(digitalSierra))) {
      case MergeResult(items, _) =>
        items should have size 3
        items should contain theSameElementsAs
          multiItemPhysicalSierra.data.items ++ digitalSierra.data.items
    }
  }

  it("merges locations from Miro items into single-item Sierra works") {
    inside(ItemsRule.merge(physicalSierra, List(miroWork))) {
      case MergeResult(items, _) =>
        items should have size 1
        items.head.locations should contain theSameElementsAs
          physicalSierra.data.items.head.locations ++ miroWork.data.items.head.locations
    }
  }

  it(
    "merges non-duplicate-URL locations from METS items into single-item Sierra works") {
    inside(ItemsRule.merge(digitalSierra, List(metsWorkWithSierraUrl))) {
      case MergeResult(items, _) =>
        items should have size 1
        items.head.locations should contain theSameElementsAs
          metsWorkWithSierraUrl.data.items.head.locations
    }
  }

  it("Merges items from METS works into multi-item Sierra works") {
    inside(ItemsRule.merge(multiItemPhysicalSierra, List(metsWork))) {
      case MergeResult(items, _) =>
        items should have size 3
        items should contain theSameElementsAs
          multiItemPhysicalSierra.data.items ++ metsWork.data.items
    }
  }

}
