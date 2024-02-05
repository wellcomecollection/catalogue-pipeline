package weco.pipeline.merger.rules

import org.scalatest.{Inside, LoneElement}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.locations._
import weco.catalogue.internal_model.work.generators.SourceWorkGenerators
import weco.catalogue.internal_model.work.{Format, Work, WorkState}
import weco.pipeline.matcher.generators.MergeCandidateGenerators
import weco.pipeline.merger.models.FieldMergeResult

class ItemsRuleTest
    extends AnyFunSpec
    with Matchers
    with SourceWorkGenerators
    with MergeCandidateGenerators
    with Inside
    with LoneElement {
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
      .items((1 to 2).map {
        _ =>
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

  it(
    "When merging items from sierra and calm, it replaces the calm item with the sierra one"
  ) {
    inside(ItemsRule.merge(tei, List(physicalPictureSierra, calmWork))) {
      case FieldMergeResult(items, mergedSources) =>
        items should have size 1
        items.head shouldBe physicalPictureSierra.data.items.head
        mergedSources should be(Seq(physicalPictureSierra))
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

  describe("Sierra physical/digital items") {
    def createMergeableDigitalItem = createDigitalItemWith(locations =
      List(
        createDigitalLocationWith(
          locationType = LocationType.OnlineResource,
          accessConditions = List(
            AccessCondition(
              method = AccessMethod.ViewOnline,
              status = AccessStatus.LicensedResources()
            )
          )
        )
      )
    )

    it("merges an 856 item from a digitised Sierra work into a physical work") {

      val digitisedWork =
        sierraIdentifiedWork().items(List(createMergeableDigitalItem))
      val physicalWork =
        sierraIdentifiedWork()
          .mergeCandidates(
            List(createSierraPairMergeCandidateFor(digitisedWork))
          )
          .items(List(createIdentifiedPhysicalItem))

      inside(ItemsRule.merge(physicalWork, List(digitisedWork))) {
        case FieldMergeResult(items, mergedSources) =>
          items should contain theSameElementsAs Seq(
            physicalWork.data.items.loneElement,
            digitisedWork.data.items.loneElement
          )
          mergedSources.loneElement shouldBe digitisedWork
      }
    }

    it(
      "chooses the first digital work from a source list that also contains non-digital sierra works"
    ) {

      val digitisedWork =
        sierraIdentifiedWork().items(List(createMergeableDigitalItem))

      val physicalWork =
        sierraIdentifiedWork()
          .mergeCandidates(
            List(createSierraPairMergeCandidateFor(digitisedWork))
          )
          .items(List(createIdentifiedPhysicalItem))

      val otherPhysicalWork =
        sierraIdentifiedWork()
          .mergeCandidates(Nil)
          .items(List(createIdentifiedPhysicalItem))

      inside(
        ItemsRule.merge(physicalWork, List(otherPhysicalWork, digitisedWork))
      ) {
        case FieldMergeResult(items, mergedSources) =>
          items should contain theSameElementsAs Seq(
            physicalWork.data.items.loneElement,
            digitisedWork.data.items.loneElement
          )
          mergedSources.loneElement shouldBe digitisedWork
      }
    }

    it("retains the target's items when presented with an empty source list") {

      val physicalWork =
        sierraIdentifiedWork()
          .mergeCandidates(
            List(
              createSierraPairMergeCandidateFor(
                sierraIdentifiedWork().items(List(createMergeableDigitalItem))
              )
            )
          )
          .items(List(createIdentifiedPhysicalItem))

      inside(ItemsRule.merge(physicalWork, Nil)) {
        case FieldMergeResult(items, mergedSources) =>
          items should contain theSameElementsAs Seq(
            physicalWork.data.items.loneElement
          )
          mergedSources shouldBe empty
      }
    }

    it(
      "retains the target's items if no corresponding digital record can be found in the source list"
    ) {

      val physicalWork =
        sierraIdentifiedWork()
          .mergeCandidates(
            List(
              createSierraPairMergeCandidateFor(
                sierraIdentifiedWork().items(List(createMergeableDigitalItem))
              )
            )
          )
          .items(List(createIdentifiedPhysicalItem))

      inside(ItemsRule.merge(physicalWork, Seq(sierraIdentifiedWork()))) {
        case FieldMergeResult(items, mergedSources) =>
          items should contain theSameElementsAs Seq(
            physicalWork.data.items.loneElement
          )
          mergedSources shouldBe empty
      }
    }

    it("only merges the first 856 item it finds in the mergeCandidates list") {

      val digitisedWork0 =
        sierraIdentifiedWork().items(List(createMergeableDigitalItem))
      val digitisedWork1 =
        sierraIdentifiedWork().items(List(createMergeableDigitalItem))
      val physicalItem = createIdentifiedPhysicalItem
      val physicalWork =
        sierraIdentifiedWork()
          .mergeCandidates(
            List(
              createSierraPairMergeCandidateFor(digitisedWork0),
              createSierraPairMergeCandidateFor(digitisedWork1)
            )
          )
          .items(List(physicalItem))
      // The order of source works is the reverse of the order of the mergeCandidate list
      // This shows that the mergeCandidate list has primacy in choosing the "first" one
      inside(
        ItemsRule.merge(physicalWork, List(digitisedWork1, digitisedWork0))
      ) {
        case FieldMergeResult(items, mergedSources) =>
          items should contain theSameElementsAs Seq(
            physicalItem,
            digitisedWork0.data.items.loneElement
          )
          // Only the digitised work that contributed to the merge is included in the mergedSources list.
          mergedSources.loneElement shouldBe digitisedWork0
      }
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
