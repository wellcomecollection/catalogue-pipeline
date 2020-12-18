package uk.ac.wellcome.platform.merger.services

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks._
import uk.ac.wellcome.models.work.internal._
import WorkState.{Identified, Merged}
import WorkFsm._
import SourceWork._
import uk.ac.wellcome.models.work.generators.SourceWorkGenerators

class PlatformMergerTest
    extends AnyFunSpec
    with SourceWorkGenerators
    with Matchers {
  import Merger.WorkMergingOps

  val digitalLocationCCBYNC = createDigitalLocationWith(
    license = Some(License.CCBYNC))
  val digitalLocationNoLicense = digitalLocationCCBYNC.copy(license = None)

  val sierraDigitisedWork: Work.Visible[Identified] =
    sierraDigitalIdentifiedWork()

  val sierraPhysicalWork: Work.Visible[Identified] =
    sierraPhysicalIdentifiedWork()
      .format(Format.`3DObjects`)
      .mergeCandidates(
        List(
          MergeCandidate(
            id = IdState.Identified(
              sourceIdentifier = sierraDigitisedWork.sourceIdentifier,
              canonicalId = sierraDigitisedWork.state.canonicalId),
            reason = Some("Physical/digitised Sierra work")
          )
        )
      )

  val zeroItemSierraWork: Work.Visible[Identified] =
    sierraIdentifiedWork()
      .items(List.empty)
      .format(Format.Pictures)

  private val multipleItemsSierraWork =
    sierraIdentifiedWork()
      .items((1 to 2).map { _ =>
        createIdentifiedPhysicalItem
      }.toList)
      .mergeCandidates(
        List(
          MergeCandidate(
            id = IdState.Identified(
              sourceIdentifier = sierraDigitisedWork.sourceIdentifier,
              canonicalId = sierraDigitisedWork.state.canonicalId),
            reason = Some("Physical/digitised Sierra work")
          )
        )
      )

  private val sierraDigitalWork: Work.Visible[Identified] =
    sierraIdentifiedWork()
      .items(
        List(
          createDigitalItemWith(List(digitalLocationNoLicense))
        )
      )
      .format(Format.DigitalImages)

  private val sierraPictureWork: Work.Visible[Identified] =
    sierraIdentifiedWork()
      .items(
        List(createIdentifiedPhysicalItem)
      )
      .format(Format.Pictures)

  private val miroWork: Work.Visible[Identified] = miroIdentifiedWork()

  private val metsWork: Work.Invisible[Identified] =
    metsIdentifiedWork()
      .items(List(createDigitalItemWith(List(digitalLocationCCBYNC))))
      .imageData(List(createMetsImageData.toIdentified))
      .thumbnail(
        DigitalLocationDeprecated(
          url = "https://path.to/thumbnail.jpg",
          locationType = LocationType("thumbnail-image"),
          license = Some(License.CCBY)
        )
      )
      .invisible()

  val calmWork: Work.Visible[Identified] = calmIdentifiedWork()

  private val merger = PlatformMerger

  it(
    "finds Calm || Sierra with physical item || Sierra work || Nothing as a target") {
    val worksWithCalmTarget =
      Seq(sierraDigitalWork, calmWork, sierraPhysicalWork, metsWork, miroWork)
    val worksWithSierraPhysicalTarget =
      Seq(sierraDigitalWork, sierraPhysicalWork, metsWork, miroWork)
    val worksWithSierraTarget = Seq(sierraDigitalWork, metsWork, miroWork)
    val worksWithNoTarget = Seq(metsWork, miroWork)

    val examples = Table(
      ("-works-", "-target-", "-clue-"),
      (worksWithCalmTarget, Some(calmWork), "Calm"),
      (
        worksWithSierraPhysicalTarget,
        Some(sierraPhysicalWork),
        "Sierra with physical item"),
      (worksWithSierraTarget, Some(sierraDigitalWork), "Sierra"),
      (worksWithNoTarget, None, "Non"),
    )

    forAll(examples) {
      (works: Seq[Work[Identified]],
       target: Option[Work.Visible[Identified]],
       clue: String) =>
        withClue(clue) {
          merger.findTarget(works) should be(target)
        }
    }
  }

  it(
    "merges a Sierra picture/digital image/3D object physical work with a Miro work") {
    val result = merger.merge(
      works = Seq(sierraPhysicalWork, miroWork)
    )

    result.mergedWorksWithTime(now).size shouldBe 2

    val sierraItem = sierraPhysicalWork.data.items.head
    val miroItem = miroWork.data.items.head

    val expectedMergedWork = sierraPhysicalWork
      .transition[Merged](now)
      .mapData { data =>
        data.copy(
          otherIdentifiers = sierraPhysicalWork.data.otherIdentifiers ++ miroWork.identifiers,
          thumbnail = miroWork.data.thumbnail,
          items = List(
            sierraItem.copy(
              locations = sierraItem.locations ++ miroItem.locations
            )
          ),
          imageData = miroWork.data.imageData,
        )
      }

    val expectedRedirectedWork =
      Work.Redirected[Merged](
        state = Merged(
          canonicalId = miroWork.state.canonicalId,
          sourceIdentifier = miroWork.sourceIdentifier,
          modifiedTime = now
        ),
        version = miroWork.version,
        redirect = IdState.Identified(
          canonicalId = sierraPhysicalWork.state.canonicalId,
          sourceIdentifier = sierraPhysicalWork.sourceIdentifier)
      )

    val expectedImage =
      miroWork.data.imageData.head.toInitialImageWith(
        modifiedTime = now,
        sourceWorks = SourceWorks(
          canonicalWork = expectedMergedWork.toSourceWork,
          redirectedWork = Some(miroWork.toSourceWork)
        )
      )
    result.mergedWorksWithTime(now) should contain theSameElementsAs List(
      expectedMergedWork,
      expectedRedirectedWork)
    result.mergedImagesWithTime(now) should contain theSameElementsAs List(
      expectedImage
    )
  }

  it("merges a zero-item Sierra work with a Miro work") {
    val result = merger.merge(
      works = Seq(zeroItemSierraWork, miroWork)
    )

    result.mergedWorksWithTime(now).size shouldBe 2

    val expectedMergedWork = zeroItemSierraWork
      .transition[Merged](now)
      .mapData { data =>
        data.copy(
          otherIdentifiers = data.otherIdentifiers ++ miroWork.identifiers,
          thumbnail = miroWork.data.thumbnail,
          items = miroWork.data.items,
          imageData = miroWork.data.imageData,
        )
      }

    val expectedRedirectedWork =
      Work.Redirected[Merged](
        state = Merged(
          canonicalId = miroWork.state.canonicalId,
          sourceIdentifier = miroWork.sourceIdentifier,
          modifiedTime = now),
        version = miroWork.version,
        redirect = IdState.Identified(
          canonicalId = zeroItemSierraWork.state.canonicalId,
          sourceIdentifier = zeroItemSierraWork.sourceIdentifier)
      )

    val expectedImage =
      miroWork.data.imageData.head.toInitialImageWith(
        modifiedTime = now,
        sourceWorks = SourceWorks(
          canonicalWork = expectedMergedWork.toSourceWork,
          redirectedWork = Some(miroWork.toSourceWork)
        )
      )

    result.mergedWorksWithTime(now) should contain theSameElementsAs List(
      expectedMergedWork,
      expectedRedirectedWork
    )
    result.mergedImagesWithTime(now) should contain theSameElementsAs List(
      expectedImage
    )
  }

  it(
    "merges a Sierra Sierra picture/digital image/3D object digital work with a Miro work") {
    val result = merger.merge(
      works = Seq(sierraDigitalWork, miroWork)
    )

    result.mergedWorksWithTime(now).size shouldBe 2

    val sierraItem = sierraDigitalWork.data.items.head
    val miroItem = miroWork.data.items.head

    val expectedMergedWork = sierraDigitalWork
      .transition[Merged](now)
      .mapData { data =>
        data.copy(
          otherIdentifiers = sierraDigitalWork.data.otherIdentifiers ++ miroWork.identifiers,
          thumbnail = miroWork.data.thumbnail,
          items = List(
            sierraItem.copy(
              locations = sierraItem.locations ++ miroItem.locations
            )
          ),
          imageData = miroWork.data.imageData,
        )
      }

    val expectedRedirectedWork =
      Work.Redirected[Merged](
        state = Merged(
          canonicalId = miroWork.state.canonicalId,
          sourceIdentifier = miroWork.sourceIdentifier,
          modifiedTime = now),
        version = miroWork.version,
        redirect = IdState.Identified(
          canonicalId = sierraDigitalWork.state.canonicalId,
          sourceIdentifier = sierraDigitalWork.sourceIdentifier)
      )

    val expectedImage = miroWork.data.imageData.head.toInitialImageWith(
      modifiedTime = now,
      sourceWorks = SourceWorks(
        canonicalWork = expectedMergedWork.toSourceWork,
        redirectedWork = Some(miroWork.toSourceWork)
      )
    )

    result.mergedWorksWithTime(now) should contain theSameElementsAs List(
      expectedMergedWork,
      expectedRedirectedWork)
    result.mergedImagesWithTime(now) should contain theSameElementsAs List(
      expectedImage
    )
  }

  it("does not merge a sierra work with multiple items with a linked Miro work") {
    val result = merger.merge(
      works = Seq(multipleItemsSierraWork, miroWork)
    )

    result.mergedWorksWithTime(now).size shouldBe 2

    val expectedMergedWork = multipleItemsSierraWork
      .transition[Merged](now)
      .mapData { data =>
        data.copy(
          imageData = miroWork.data.imageData,
        )
      }

    val expectedRedirectedMiro = Work.Redirected[Merged](
      state = Merged(
        canonicalId = miroWork.state.canonicalId,
        sourceIdentifier = miroWork.sourceIdentifier,
        modifiedTime = now),
      version = miroWork.version,
      redirect = IdState.Identified(
        canonicalId = multipleItemsSierraWork.state.canonicalId,
        sourceIdentifier = multipleItemsSierraWork.sourceIdentifier)
    )

    result.mergedWorksWithTime(now) should contain theSameElementsAs Seq(
      expectedRedirectedMiro,
      expectedMergedWork)
  }

  it("merges a non-picture Sierra work with a METS work") {
    val result = merger.merge(
      works = Seq(sierraPhysicalWork, metsWork)
    )

    result.mergedWorksWithTime(now).size shouldBe 2

    val physicalItem = sierraPhysicalWork.data.items.head
    val digitalItem = metsWork.data.items.head

    val expectedMergedWork = sierraPhysicalWork
      .transition[Merged](now)
      .mapData { data =>
        data.copy(
          items = List(
            physicalItem.copy(
              locations = physicalItem.locations ++ digitalItem.locations
            )
          ),
          thumbnail = metsWork.data.thumbnail,
        )
      }

    val expectedRedirectedWork =
      Work.Redirected[Merged](
        state = Merged(
          canonicalId = metsWork.state.canonicalId,
          sourceIdentifier = metsWork.sourceIdentifier,
          modifiedTime = now),
        version = metsWork.version,
        redirect = IdState.Identified(
          canonicalId = sierraPhysicalWork.state.canonicalId,
          sourceIdentifier = sierraPhysicalWork.sourceIdentifier)
      )

    result.mergedWorksWithTime(now) should contain theSameElementsAs List(
      expectedMergedWork,
      expectedRedirectedWork)
    result.mergedImagesWithTime(now) shouldBe empty
  }

  it("merges a picture Sierra work with a METS work") {
    val result = merger.merge(
      works = Seq(sierraPictureWork, metsWork)
    )

    result.mergedWorksWithTime(now).size shouldBe 2

    val physicalItem = sierraPictureWork.data.items.head
    val digitalItem = metsWork.data.items.head

    val expectedMergedWork = sierraPictureWork
      .transition[Merged](now)
      .mapData { data =>
        data.copy(
          items = List(
            physicalItem.copy(
              locations = physicalItem.locations ++ digitalItem.locations
            )
          ),
          imageData = metsWork.data.imageData,
          thumbnail = metsWork.data.thumbnail,
        )
      }

    val expectedRedirectedWork =
      Work.Redirected[Merged](
        state = Merged(
          canonicalId = metsWork.state.canonicalId,
          sourceIdentifier = metsWork.sourceIdentifier,
          modifiedTime = now),
        version = metsWork.version,
        redirect = IdState.Identified(
          canonicalId = sierraPictureWork.state.canonicalId,
          sourceIdentifier = sierraPictureWork.sourceIdentifier)
      )

    val expectedImage =
      metsWork.data.imageData.head.toInitialImageWith(
        modifiedTime = now,
        sourceWorks = SourceWorks(
          canonicalWork = expectedMergedWork.toSourceWork,
          redirectedWork = Some(metsWork.toSourceWork)
        )
      )

    result.mergedWorksWithTime(now) should contain theSameElementsAs List(
      expectedMergedWork,
      expectedRedirectedWork)
    result.mergedImagesWithTime(now) should contain theSameElementsAs List(
      expectedImage
    )
  }

  it(
    "merges a 3D object physical Sierra work with a digital Sierra work, a Miro work and a METS work") {
    val result = merger.merge(
      works = Seq(sierraPhysicalWork, sierraDigitisedWork, miroWork, metsWork)
    )

    result.mergedWorksWithTime(now).size shouldBe 4

    val sierraItem = sierraPhysicalWork.data.items.head
    val metsItem = metsWork.data.items.head

    val expectedMergedWork = sierraPhysicalWork
      .transition[Merged](now)
      .mapData { data =>
        data.copy(
          otherIdentifiers = sierraPhysicalWork.data.otherIdentifiers
            ++ sierraDigitisedWork.identifiers
            ++ miroWork.identifiers,
          thumbnail = metsWork.data.thumbnail,
          items = List(
            sierraItem.copy(
              locations = sierraItem.locations ++ metsItem.locations
            )
          ),
          imageData = miroWork.data.imageData,
        )
      }

    val expectedRedirectedDigitalWork =
      Work.Redirected[Merged](
        state = Merged(
          canonicalId = sierraDigitisedWork.state.canonicalId,
          sourceIdentifier = sierraDigitisedWork.sourceIdentifier,
          modifiedTime = now),
        version = sierraDigitisedWork.version,
        redirect = IdState.Identified(
          canonicalId = sierraPhysicalWork.state.canonicalId,
          sourceIdentifier = sierraPhysicalWork.sourceIdentifier)
      )

    val expectedMiroRedirectedWork =
      Work.Redirected[Merged](
        state = Merged(
          canonicalId = miroWork.state.canonicalId,
          sourceIdentifier = miroWork.sourceIdentifier,
          modifiedTime = now),
        version = miroWork.version,
        redirect = IdState.Identified(
          canonicalId = sierraPhysicalWork.state.canonicalId,
          sourceIdentifier = sierraPhysicalWork.sourceIdentifier)
      )

    val expectedMetsRedirectedWork =
      Work.Redirected[Merged](
        state = Merged(
          canonicalId = metsWork.state.canonicalId,
          sourceIdentifier = metsWork.sourceIdentifier,
          modifiedTime = now),
        version = metsWork.version,
        redirect = IdState.Identified(
          canonicalId = sierraPhysicalWork.state.canonicalId,
          sourceIdentifier = sierraPhysicalWork.sourceIdentifier)
      )

    val expectedImage = miroWork.data.imageData.head.toInitialImageWith(
      modifiedTime = now,
      sourceWorks = SourceWorks(
        canonicalWork = expectedMergedWork.toSourceWork,
        redirectedWork = Some(miroWork.toSourceWork)
      )
    )

    result.mergedWorksWithTime(now) should contain theSameElementsAs List(
      expectedMergedWork,
      expectedRedirectedDigitalWork,
      expectedMiroRedirectedWork,
      expectedMetsRedirectedWork)
    result.mergedImagesWithTime(now) should contain theSameElementsAs List(
      expectedImage
    )
  }

  it("merges a multiple items physical Sierra work with a METS work") {
    val result = merger.merge(
      works = Seq(multipleItemsSierraWork, metsWork)
    )

    result.mergedWorksWithTime(now).size shouldBe 2

    val sierraItems =
      multipleItemsSierraWork.data.items
    val metsItem = metsWork.data.items.head

    val expectedMergedWork = multipleItemsSierraWork
      .transition[Merged](now)
      .mapData { data =>
        data.copy(
          thumbnail = metsWork.data.thumbnail,
          items = sierraItems :+ metsItem,
        )
      }

    val expectedMetsRedirectedWork =
      Work.Redirected[Merged](
        state = Merged(
          canonicalId = metsWork.state.canonicalId,
          sourceIdentifier = metsWork.sourceIdentifier,
          modifiedTime = now),
        version = metsWork.version,
        redirect = IdState.Identified(
          canonicalId = multipleItemsSierraWork.state.canonicalId,
          sourceIdentifier = multipleItemsSierraWork.sourceIdentifier)
      )

    result.mergedWorksWithTime(now) should contain theSameElementsAs List(
      expectedMergedWork,
      expectedMetsRedirectedWork)
    result.mergedImagesWithTime(now) shouldBe empty
  }

  it(
    "merges a multiple items physical Sierra work with a digital Sierra work and a METS work") {
    val result = merger.merge(
      works = Seq(multipleItemsSierraWork, sierraDigitisedWork, metsWork)
    )

    result.mergedWorksWithTime(now).size shouldBe 3

    val sierraItems = multipleItemsSierraWork.data.items
    val metsItem = metsWork.data.items.head

    val expectedMergedWork = multipleItemsSierraWork
      .transition[Merged](now)
      .mapData { data =>
        data.copy(
          otherIdentifiers = multipleItemsSierraWork.data.otherIdentifiers ++ sierraDigitisedWork.identifiers,
          thumbnail = metsWork.data.thumbnail,
          items = sierraItems :+ metsItem,
        )
      }

    val expectedRedirectedDigitalWork =
      Work.Redirected[Merged](
        state = Merged(
          canonicalId = sierraDigitisedWork.state.canonicalId,
          sourceIdentifier = sierraDigitisedWork.sourceIdentifier,
          modifiedTime = now),
        version = sierraDigitisedWork.version,
        redirect = IdState.Identified(
          canonicalId = multipleItemsSierraWork.state.canonicalId,
          sourceIdentifier = multipleItemsSierraWork.sourceIdentifier)
      )

    val expectedMetsRedirectedWork =
      Work.Redirected[Merged](
        state = Merged(
          canonicalId = metsWork.state.canonicalId,
          sourceIdentifier = metsWork.sourceIdentifier,
          modifiedTime = now),
        version = metsWork.version,
        redirect = IdState.Identified(
          canonicalId = multipleItemsSierraWork.state.canonicalId,
          sourceIdentifier = multipleItemsSierraWork.sourceIdentifier)
      )

    result.mergedWorksWithTime(now) should contain theSameElementsAs List(
      expectedMergedWork,
      expectedRedirectedDigitalWork,
      expectedMetsRedirectedWork)

    result.mergedImagesWithTime(now) shouldBe empty
  }

  it("creates an image for a single Miro target") {
    val result = merger.merge(List(miroWork))

    result.mergedWorksWithTime(now) should have length 1
    result.mergedWorksWithTime(now).head shouldBe miroWork.transition[Merged](now)
    result.mergedImagesWithTime(now) should have length 1
    result.mergedImagesWithTime(now).head shouldBe miroWork.data.imageData.head
      .toInitialImageWith(
        modifiedTime = now,
        sourceWorks = SourceWorks(
          canonicalWork = miroWork.toSourceWork,
          redirectedWork = None
        )
      )
  }
}
