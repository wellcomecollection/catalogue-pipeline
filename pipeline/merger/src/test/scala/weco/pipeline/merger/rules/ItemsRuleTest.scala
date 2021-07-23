package weco.pipeline.merger.rules

import org.scalatest.Inside
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.locations.{
  AccessCondition,
  AccessMethod,
  AccessStatus,
  DigitalLocation,
  LocationType
}
import weco.catalogue.internal_model.work.generators.SourceWorkGenerators
import weco.catalogue.internal_model.work.{
  Format,
  Item,
  MergeCandidate,
  Work,
  WorkState
}
import weco.pipeline.merger.models.FieldMergeResult

class ItemsRuleTest
    extends AnyFunSpec
    with Matchers
    with SourceWorkGenerators
    with Inside {
  val tei: Work.Visible[WorkState.Identified] =
    teiIdentifiedWork()

  val physicalPictureSierra: Work.Visible[WorkState.Identified] =
    sierraPhysicalIdentifiedWork()
      .format(Format.Pictures)

  val physicalMapsSierra: Work.Visible[WorkState.Identified] =
    sierraPhysicalIdentifiedWork().format(Format.Maps)

  val zeroItemPhysicalSierra: Work.Visible[WorkState.Identified] =
    sierraIdentifiedWork().format(Format.Pictures)

  val multiItemPhysicalSierra: Work.Visible[WorkState.Identified] =
    sierraIdentifiedWork()
      .items((1 to 2).map { _ =>
        createIdentifiedPhysicalItem
      }.toList)

  val metsWork: Work.Invisible[WorkState.Identified] =
    metsIdentifiedWork().invisible()

  val miroWork: Work.Visible[WorkState.Identified] = miroIdentifiedWork()

  val calmWork: Work.Visible[WorkState.Identified] = calmIdentifiedWork()

  it(
    "leaves items unchanged and returns a digitised version of a Sierra work as a merged source"
  ) {
    val (digitisedWork, physicalWork) = sierraIdentifiedWorkPair()

    inside(
      ItemsRule
        .merge(physicalWork, List(digitisedWork))
    ) {
      case FieldMergeResult(items, mergedSources) =>
        items should have size 1
        items shouldBe physicalWork.data.items

        mergedSources should be(Seq(digitisedWork))
    }
  }

  // Sierra zero item
  it("merges the item from Miro works into zero-item Sierra works") {
    inside(ItemsRule.merge(zeroItemPhysicalSierra, List(miroWork))) {
      case FieldMergeResult(items, mergedSources) =>
        items should have size 1
        items.head shouldBe miroWork.data.items.head
        mergedSources should be(Seq(miroWork))
    }
  }

  it("merges the item from Sierra works tei works") {
    inside(ItemsRule.merge(tei, List(multiItemPhysicalSierra))) {
      case FieldMergeResult(items, mergedSources) =>
        items should have size 2
        items.head shouldBe multiItemPhysicalSierra.data.items.head
        mergedSources should be(Seq(multiItemPhysicalSierra))
    }
  }

  it("merges the item from METS works into zero-item Sierra works") {
    inside(ItemsRule.merge(zeroItemPhysicalSierra, List(metsWork))) {
      case FieldMergeResult(items, mergedSources) =>
        items should have size 1
        items.head shouldBe metsWork.data.items.head
        mergedSources should be(Seq(metsWork))
    }
  }

  it("merges an 856 item from a digitised Sierra work into a physical work") {
    val item = Item(
      id = IdState.Unidentifiable,
      locations = List(
        DigitalLocation(
          url = "https://example.org/b12345678",
          locationType = LocationType.OnlineResource,
          accessConditions = List(
            AccessCondition(
              method = AccessMethod.ViewOnline,
              status = AccessStatus.LicensedResources
            )
          )
        )
      )
    )

    val digitisedWork = sierraIdentifiedWork().items(List(item))

    val physicalWork =
      sierraIdentifiedWork()
        .mergeCandidates(
          List(
            MergeCandidate(
              id = IdState.Identified(
                canonicalId = digitisedWork.state.canonicalId,
                sourceIdentifier = digitisedWork.state.sourceIdentifier
              ),
              reason = "Physical/digitised Sierra work"
            )
          )
        )
        .items(List(createIdentifiedPhysicalItem))

    inside(ItemsRule.merge(physicalWork, List(digitisedWork))) {
      case FieldMergeResult(items, mergedSources) =>
        items should contain(item)
        mergedSources should be(Seq(digitisedWork))
    }
  }

  // Sierra single item
  it("merges locations from Miro items into single-item Sierra works") {
    inside(ItemsRule.merge(physicalPictureSierra, List(miroWork))) {
      case FieldMergeResult(items, mergedSources) =>
        items should have size 1
        items.head.locations should contain theSameElementsAs
          physicalPictureSierra.data.items.head.locations ++ miroWork.data.items.head.locations

        mergedSources should be(Seq(miroWork))
    }
  }

  it("merges item locations in METS work into single-item Sierra works item") {
    inside(ItemsRule.merge(physicalPictureSierra, List(metsWork))) {
      case FieldMergeResult(items, mergedSources) =>
        items should have size 1
        items.head.locations shouldBe
          physicalPictureSierra.data.items.head.locations ++
            metsWork.data.items.head.locations

        mergedSources should be(Seq(metsWork))
    }
  }

  it("does not merge any Miro sources when there are several of them") {
    inside(
      ItemsRule
        .merge(physicalPictureSierra, List(miroWork, miroIdentifiedWork()))
    ) {
      case FieldMergeResult(items, mergedSources) =>
        items shouldEqual physicalPictureSierra.data.items
        mergedSources shouldBe empty
    }
  }

  it(
    "does not merge a Miro source into a Sierra work with format != picture/digital image/3D object"
  ) {
    inside(ItemsRule.merge(physicalMapsSierra, List(miroWork))) {
      case FieldMergeResult(items, mergedSources) =>
        items shouldEqual physicalMapsSierra.data.items
        mergedSources shouldBe empty
    }
  }

  it(
    "override Miro merging with METS merging into single-item physical Sierra works items"
  ) {
    inside(ItemsRule.merge(physicalPictureSierra, List(miroWork, metsWork))) {
      case FieldMergeResult(items, mergedSources) =>
        items should have size 1
        items.head.locations shouldBe
          physicalPictureSierra.data.items.head.locations ++
            metsWork.data.items.head.locations

        mergedSources should contain theSameElementsAs Seq(metsWork, miroWork)
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

  it(
    "doesn't merge Miro works into multi-item Sierra digaids works alongside METS items, but does treat them as mergedSources"
  ) {
    inside(
      ItemsRule.merge(
        multiItemPhysicalSierra
          .otherIdentifiers(List(createDigcodeIdentifier("digaids"))),
        List(miroWork, metsWork)
      )
    ) {
      case FieldMergeResult(items, mergedSources) =>
        items should be(
          multiItemPhysicalSierra.data.items ++ metsWork.data.items
        )
        mergedSources should contain theSameElementsAs Seq(miroWork, metsWork)
    }
  }

  // Calm
  it("Adds Sierra item IDs to Calm item") {
    inside(ItemsRule.merge(calmWork, List(physicalPictureSierra))) {
      case FieldMergeResult(items, mergedSources) =>
        items should have size 1
        items.head.id should be(physicalPictureSierra.data.items.head.id)

        mergedSources should be(Seq(physicalPictureSierra))
    }
  }

  it("copies the locations from Sierra to Calm") {
    val ac = AccessCondition(
      method = AccessMethod.OnlineRequest,
      status = AccessStatus.Open
    )

    val location = createPhysicalLocationWith(accessConditions = List(ac))

    val item = createIdentifiedItemWith(locations = List(location))

    val sierraWork = sierraIdentifiedWork().items(List(item))

    inside(ItemsRule.merge(calmWork, List(sierraWork))) {
      case FieldMergeResult(items, mergedSources) =>
        items should have size 1
        items.head.locations shouldBe Seq(location)

        mergedSources shouldBe Seq(sierraWork)
    }
  }

  it("replace Calm items with Sierra and METS items") {
    inside(ItemsRule.merge(calmWork, List(physicalPictureSierra, metsWork))) {
      case FieldMergeResult(items, mergedSources) =>
        items should have size 2

        items shouldBe physicalPictureSierra.data.items ++ metsWork.data.items
    }
  }
}
