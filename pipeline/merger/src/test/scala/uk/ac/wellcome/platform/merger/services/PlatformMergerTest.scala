package uk.ac.wellcome.platform.merger.services

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks._
import uk.ac.wellcome.models.work.internal._
import WorkState.{Merged, Source}
import WorkFsm._
import SourceWork._
import uk.ac.wellcome.models.work.generators.{
  MetsWorkGenerators,
  MiroWorkGenerators
}

class PlatformMergerTest
    extends AnyFunSpec
    with MetsWorkGenerators
    with MiroWorkGenerators
    with Matchers {
  val digitalLocationCCBYNC = createDigitalLocationWith(
    license = Some(License.CCBYNC))
  val digitalLocationNoLicense = digitalLocationCCBYNC.copy(license = None)

  val sierraDigitisedWork: Work.Visible[Source] =
    sierraDigitalSourceWork()

  val sierraPhysicalWork: Work.Visible[Source] =
    sierraPhysicalSourceWork()
      .format(Format.`3DObjects`)
      .mergeCandidates(
        List(
          MergeCandidate(
            identifier = sierraDigitisedWork.sourceIdentifier,
            reason = "Physical/digitised Sierra work"
          )
        )
      )

  val zeroItemSierraWork: Work.Visible[Source] =
    sierraSourceWork()
      .items(List.empty)
      .format(Format.Pictures)

  private val multipleItemsSierraWork =
    sierraSourceWork()
      .items((1 to 2).map { _ =>
        createPhysicalItem
      }.toList)
      .mergeCandidates(
        List(
          MergeCandidate(
            identifier = sierraDigitisedWork.sourceIdentifier,
            reason = "Physical/digitised Sierra work"
          )
        )
      )

  private val sierraDigitalWork: Work.Visible[Source] =
    sierraSourceWork()
      .items(
        List(
          createDigitalItemWith(List(digitalLocationNoLicense))
        )
      )
      .format(Format.DigitalImages)

  private val sierraPictureWork: Work.Visible[Source] =
    sierraSourceWork()
      .items(
        List(createPhysicalItem)
      )
      .format(Format.Pictures)

  private val miroWork: Work.Visible[Source] = miroSourceWork()

  private val metsWork: Work.Invisible[Source] =
    metsSourceWork()
      .items(List(createDigitalItemWith(List(digitalLocationCCBYNC))))
      .images(List(createUnmergedMetsImage))
      .thumbnail(
        DigitalLocationDeprecated(
          url = "https://path.to/thumbnail.jpg",
          locationType = LocationType("thumbnail-image"),
          license = Some(License.CCBY)
        )
      )
      .invisible()

  val calmWork: Work.Visible[Source] =
    sourceWork(sourceIdentifier = createCalmSourceIdentifier)
      .items(List(createCalmItem))

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
      (works: Seq[Work[Source]],
       target: Option[Work.Visible[Source]],
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
      .transition[Merged](Some(now))
      .mapData { data =>
        data.copy(
          otherIdentifiers = sierraPhysicalWork.data.otherIdentifiers ++ miroWork.identifiers,
          thumbnail = miroWork.data.thumbnail,
          items = List(
            sierraItem.copy(
              locations = sierraItem.locations ++ miroItem.locations
            )
          ),
          images = miroWork.data.images,
        )
      }

    val expectedRedirectedWork =
      Work.Redirected[Merged](
        state = Merged(
          sourceIdentifier = miroWork.sourceIdentifier,
          modifiedTime = now
        ),
        version = miroWork.version,
        redirect = IdState.Identifiable(sierraPhysicalWork.sourceIdentifier)
      )

    val expectedImage = miroWork.data.images.head mergeWith (
      canonicalWork = expectedMergedWork.toSourceWork,
      redirectedWork = Some(miroWork.toSourceWork),
      modifiedTime = now
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
      .transition[Merged](Some(now))
      .mapData { data =>
        data.copy(
          otherIdentifiers = data.otherIdentifiers ++ miroWork.identifiers,
          thumbnail = miroWork.data.thumbnail,
          items = miroWork.data.items,
          images = miroWork.data.images,
        )
      }

    val expectedRedirectedWork =
      Work.Redirected[Merged](
        state = Merged(
          sourceIdentifier = miroWork.sourceIdentifier,
          modifiedTime = now),
        version = miroWork.version,
        redirect = IdState.Identifiable(zeroItemSierraWork.sourceIdentifier)
      )

    val expectedImage = miroWork.data.images.head mergeWith (
      canonicalWork = expectedMergedWork.toSourceWork,
      redirectedWork = Some(miroWork.toSourceWork),
      modifiedTime = now
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
      .transition[Merged](Some(now))
      .mapData { data =>
        data.copy(
          otherIdentifiers = sierraDigitalWork.data.otherIdentifiers ++ miroWork.identifiers,
          thumbnail = miroWork.data.thumbnail,
          items = List(
            sierraItem.copy(
              locations = sierraItem.locations ++ miroItem.locations
            )
          ),
          images = miroWork.data.images,
        )
      }

    val expectedRedirectedWork =
      Work.Redirected[Merged](
        state = Merged(
          sourceIdentifier = miroWork.sourceIdentifier,
          modifiedTime = now),
        version = miroWork.version,
        redirect = IdState.Identifiable(sierraDigitalWork.sourceIdentifier)
      )

    val expectedImage = miroWork.data.images.head mergeWith (
      canonicalWork = expectedMergedWork.toSourceWork,
      redirectedWork = Some(miroWork.toSourceWork),
      modifiedTime = now
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
      .transition[Merged](Some(now))
      .mapData { data =>
        data.copy(
          images = miroWork.data.images,
        )
      }

    val expectedRedirectedMiro = Work.Redirected[Merged](
      state = Merged(
        sourceIdentifier = miroWork.sourceIdentifier,
        modifiedTime = now),
      version = miroWork.version,
      redirect = IdState.Identifiable(multipleItemsSierraWork.sourceIdentifier)
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
      .transition[Merged](Some(now))
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
          sourceIdentifier = metsWork.sourceIdentifier,
          modifiedTime = now),
        version = metsWork.version,
        redirect = IdState.Identifiable(sierraPhysicalWork.sourceIdentifier)
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
      .transition[Merged](Some(now))
      .mapData { data =>
        data.copy(
          items = List(
            physicalItem.copy(
              locations = physicalItem.locations ++ digitalItem.locations
            )
          ),
          images = metsWork.data.images,
          thumbnail = metsWork.data.thumbnail,
        )
      }

    val expectedRedirectedWork =
      Work.Redirected[Merged](
        state = Merged(
          sourceIdentifier = metsWork.sourceIdentifier,
          modifiedTime = now),
        version = metsWork.version,
        redirect = IdState.Identifiable(sierraPictureWork.sourceIdentifier)
      )

    val expectedImage = metsWork.data.images.head mergeWith (
      canonicalWork = expectedMergedWork.toSourceWork,
      redirectedWork = Some(metsWork.toSourceWork),
      modifiedTime = now
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
      .transition[Merged](Some(now))
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
          images = miroWork.data.images,
        )
      }

    val expectedRedirectedDigitalWork =
      Work.Redirected[Merged](
        state = Merged(
          sourceIdentifier = sierraDigitisedWork.sourceIdentifier,
          modifiedTime = now),
        version = sierraDigitisedWork.version,
        redirect = IdState.Identifiable(sierraPhysicalWork.sourceIdentifier)
      )

    val expectedMiroRedirectedWork =
      Work.Redirected[Merged](
        state = Merged(
          sourceIdentifier = miroWork.sourceIdentifier,
          modifiedTime = now),
        version = miroWork.version,
        redirect = IdState.Identifiable(sierraPhysicalWork.sourceIdentifier)
      )

    val expectedMetsRedirectedWork =
      Work.Redirected[Merged](
        state = Merged(
          sourceIdentifier = metsWork.sourceIdentifier,
          modifiedTime = now),
        version = metsWork.version,
        redirect = IdState.Identifiable(sierraPhysicalWork.sourceIdentifier)
      )

    val expectedImage = miroWork.data.images.head mergeWith (
      canonicalWork = expectedMergedWork.toSourceWork,
      redirectedWork = Some(miroWork.toSourceWork),
      modifiedTime = now
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
      .transition[Merged](Some(now))
      .mapData { data =>
        data.copy(
          thumbnail = metsWork.data.thumbnail,
          items = sierraItems :+ metsItem,
        )
      }

    val expectedMetsRedirectedWork =
      Work.Redirected[Merged](
        state = Merged(
          sourceIdentifier = metsWork.sourceIdentifier,
          modifiedTime = now),
        version = metsWork.version,
        redirect =
          IdState.Identifiable(multipleItemsSierraWork.sourceIdentifier)
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
      .transition[Merged](Some(now))
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
          sourceIdentifier = sierraDigitisedWork.sourceIdentifier,
          modifiedTime = now),
        version = sierraDigitisedWork.version,
        redirect =
          IdState.Identifiable(multipleItemsSierraWork.sourceIdentifier)
      )

    val expectedMetsRedirectedWork =
      Work.Redirected[Merged](
        state = Merged(
          sourceIdentifier = metsWork.sourceIdentifier,
          modifiedTime = now),
        version = metsWork.version,
        redirect =
          IdState.Identifiable(multipleItemsSierraWork.sourceIdentifier)
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
    result.mergedWorksWithTime(now).head shouldBe miroWork.transition[Merged](
      Some(now))
    result.mergedImagesWithTime(now) should have length 1
    result.mergedImagesWithTime(now).head shouldBe miroWork.data.images.head
      .mergeWith(
        canonicalWork = miroWork.toSourceWork,
        redirectedWork = None,
        modifiedTime = now
      )
  }
}
