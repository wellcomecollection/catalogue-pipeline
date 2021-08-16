package weco.pipeline.merger.services

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks._
import weco.catalogue.internal_model.work.WorkState.{Identified, Merged}
import weco.catalogue.internal_model.work.WorkFsm._
import weco.catalogue.internal_model.image.ParentWork._
import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.image.ParentWorks
import weco.catalogue.internal_model.locations.{
  AccessCondition,
  AccessMethod,
  AccessStatus,
  DigitalLocation,
  License,
  LocationType
}
import weco.catalogue.internal_model.work.generators.SourceWorkGenerators
import weco.catalogue.internal_model.work.{Format, Item, MergeCandidate, Work}

class PlatformMergerTest
    extends AnyFunSpec
    with SourceWorkGenerators
    with Matchers {

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
            reason = "Physical/digitised Sierra work"
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
            reason = "Physical/digitised Sierra work"
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
        DigitalLocation(
          url = "https://path.to/thumbnail.jpg",
          locationType = LocationType.ThumbnailImage,
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

    val expectedMergedWork = Work.Visible[Merged](
      version = sierraPhysicalWork.version,
      data = sierraPhysicalWork.data.copy(
        otherIdentifiers = sierraPhysicalWork.data.otherIdentifiers ++ miroWork.identifiers,
        thumbnail = miroWork.data.thumbnail,
        items = List(
          sierraItem.copy(
            locations = sierraItem.locations ++ miroItem.locations
          )
        ),
        imageData = miroWork.data.imageData,
      ),
      state = sierraPhysicalWork.transition[Merged](now).state,
      redirectSources = Seq(
        IdState
          .Identified(miroWork.state.canonicalId, miroWork.sourceIdentifier)
      )
    )

    val expectedRedirectedWork =
      Work.Redirected[Merged](
        state = Merged(
          canonicalId = miroWork.state.canonicalId,
          sourceIdentifier = miroWork.sourceIdentifier,
          sourceModifiedTime = miroWork.state.sourceModifiedTime,
          mergedTime = now
        ),
        version = miroWork.version,
        redirectTarget = IdState.Identified(
          canonicalId = sierraPhysicalWork.state.canonicalId,
          sourceIdentifier = sierraPhysicalWork.sourceIdentifier)
      )

    val expectedImage =
      miroWork.data.imageData.head.toInitialImageWith(
        modifiedTime = now,
        parentWorks = ParentWorks(
          canonicalWork = expectedMergedWork.toParentWork,
          redirectedWork = Some(miroWork.toParentWork)
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

    val expectedMergedWork = Work.Visible[Merged](
      version = zeroItemSierraWork.version,
      data = zeroItemSierraWork.data.copy(
        otherIdentifiers = zeroItemSierraWork.data.otherIdentifiers ++ miroWork.identifiers,
        thumbnail = miroWork.data.thumbnail,
        items = miroWork.data.items,
        imageData = miroWork.data.imageData,
      ),
      state = zeroItemSierraWork.transition[Merged](now).state,
      redirectSources = Seq(
        IdState
          .Identified(miroWork.state.canonicalId, miroWork.sourceIdentifier)
      )
    )

    val expectedRedirectedWork =
      Work.Redirected[Merged](
        state = Merged(
          canonicalId = miroWork.state.canonicalId,
          sourceIdentifier = miroWork.sourceIdentifier,
          sourceModifiedTime = miroWork.state.sourceModifiedTime,
          mergedTime = now),
        version = miroWork.version,
        redirectTarget = IdState.Identified(
          canonicalId = zeroItemSierraWork.state.canonicalId,
          sourceIdentifier = zeroItemSierraWork.sourceIdentifier)
      )

    val expectedImage =
      miroWork.data.imageData.head.toInitialImageWith(
        modifiedTime = now,
        parentWorks = ParentWorks(
          canonicalWork = expectedMergedWork.toParentWork,
          redirectedWork = Some(miroWork.toParentWork)
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
    "merges a Sierra picture/digital image/3D object digital work with a Miro work") {
    val result = merger.merge(
      works = Seq(sierraDigitalWork, miroWork)
    )

    result.mergedWorksWithTime(now).size shouldBe 2

    val sierraItem = sierraDigitalWork.data.items.head
    val miroItem = miroWork.data.items.head

    val expectedMergedWork = Work.Visible[Merged](
      version = sierraDigitalWork.version,
      data = sierraDigitalWork.data.copy(
        otherIdentifiers = sierraDigitalWork.data.otherIdentifiers ++ miroWork.identifiers,
        thumbnail = miroWork.data.thumbnail,
        items = List(
          sierraItem.copy(
            locations = sierraItem.locations ++ miroItem.locations
          )
        ),
        imageData = miroWork.data.imageData,
      ),
      state = sierraDigitalWork.transition[Merged](now).state,
      redirectSources = Seq(
        IdState
          .Identified(miroWork.state.canonicalId, miroWork.sourceIdentifier)
      )
    )

    val expectedRedirectedWork =
      Work.Redirected[Merged](
        state = Merged(
          canonicalId = miroWork.state.canonicalId,
          sourceIdentifier = miroWork.sourceIdentifier,
          sourceModifiedTime = miroWork.state.sourceModifiedTime,
          mergedTime = now),
        version = miroWork.version,
        redirectTarget = IdState.Identified(
          canonicalId = sierraDigitalWork.state.canonicalId,
          sourceIdentifier = sierraDigitalWork.sourceIdentifier)
      )

    val expectedImage = miroWork.data.imageData.head.toInitialImageWith(
      modifiedTime = now,
      parentWorks = ParentWorks(
        canonicalWork = expectedMergedWork.toParentWork,
        redirectedWork = Some(miroWork.toParentWork)
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

    val expectedMergedWork = Work.Visible[Merged](
      version = multipleItemsSierraWork.version,
      data = multipleItemsSierraWork.data.copy(
        imageData = miroWork.data.imageData,
      ),
      state = multipleItemsSierraWork.transition[Merged](now).state,
      redirectSources = Seq(
        IdState
          .Identified(miroWork.state.canonicalId, miroWork.sourceIdentifier)
      )
    )

    val expectedRedirectedMiro = Work.Redirected[Merged](
      state = Merged(
        canonicalId = miroWork.state.canonicalId,
        sourceIdentifier = miroWork.sourceIdentifier,
        sourceModifiedTime = miroWork.state.sourceModifiedTime,
        mergedTime = now),
      version = miroWork.version,
      redirectTarget = IdState.Identified(
        canonicalId = multipleItemsSierraWork.state.canonicalId,
        sourceIdentifier = multipleItemsSierraWork.sourceIdentifier)
    )

    result.mergedWorksWithTime(now) should contain theSameElementsAs Seq(
      expectedRedirectedMiro,
      expectedMergedWork)
  }

  describe("merges a non-picture Sierra work with a METS work") {
    val physicalItem = sierraPhysicalWork.data.items.head
    val digitalItem = metsWork.data.items.head

    val expectedMergedWork = Work.Visible[Merged](
      version = sierraPhysicalWork.version,
      data = sierraPhysicalWork.data.copy(
        items = List(
          physicalItem.copy(
            locations = physicalItem.locations ++ digitalItem.locations
          )
        ),
        thumbnail = metsWork.data.thumbnail,
      ),
      state = sierraPhysicalWork.transition[Merged](now).state,
      redirectSources = Seq(
        IdState
          .Identified(metsWork.state.canonicalId, metsWork.sourceIdentifier)
      )
    )

    val expectedRedirectedWork =
      Work.Redirected[Merged](
        state = Merged(
          canonicalId = metsWork.state.canonicalId,
          sourceIdentifier = metsWork.sourceIdentifier,
          sourceModifiedTime = metsWork.state.sourceModifiedTime,
          mergedTime = now),
        version = metsWork.version,
        redirectTarget = IdState.Identified(
          canonicalId = sierraPhysicalWork.state.canonicalId,
          sourceIdentifier = sierraPhysicalWork.sourceIdentifier)
      )

    it("merges the two Works") {
      val result = merger.merge(
        works = Seq(sierraPhysicalWork, metsWork)
      )

      result.mergedWorksWithTime(now).size shouldBe 2

      result.mergedWorksWithTime(now) should contain(expectedMergedWork)
      result.mergedWorksWithTime(now) should contain(expectedRedirectedWork)

      result.mergedImagesWithTime(now) shouldBe empty
    }

    it("ignores a deleted Work when deciding how to merge the other Works") {
      val deletedWork = identifiedWork().deleted()

      val result = merger.merge(
        works = Seq(sierraPhysicalWork, metsWork, deletedWork)
      )

      result.mergedWorksWithTime(now).size shouldBe 3

      result.mergedWorksWithTime(now) should contain(expectedMergedWork)
      result.mergedWorksWithTime(now) should contain(expectedRedirectedWork)
      result.mergedWorksWithTime(now) should contain(
        deletedWork.transition[Merged](now))

      result.mergedImagesWithTime(now) shouldBe empty
    }
  }

  it("merges a picture Sierra work with a METS work") {
    val result = merger.merge(
      works = Seq(sierraPictureWork, metsWork)
    )

    result.mergedWorksWithTime(now).size shouldBe 2

    val physicalItem = sierraPictureWork.data.items.head
    val digitalItem = metsWork.data.items.head

    val expectedMergedWork = Work.Visible[Merged](
      version = sierraPictureWork.version,
      data = sierraPictureWork.data.copy(
        items = List(
          physicalItem.copy(
            locations = physicalItem.locations ++ digitalItem.locations
          )
        ),
        imageData = metsWork.data.imageData,
        thumbnail = metsWork.data.thumbnail,
      ),
      state = sierraPictureWork.transition[Merged](now).state,
      redirectSources = Seq(
        IdState
          .Identified(metsWork.state.canonicalId, metsWork.sourceIdentifier)
      )
    )

    val expectedRedirectedWork =
      Work.Redirected[Merged](
        state = Merged(
          canonicalId = metsWork.state.canonicalId,
          sourceIdentifier = metsWork.sourceIdentifier,
          sourceModifiedTime = metsWork.state.sourceModifiedTime,
          mergedTime = now),
        version = metsWork.version,
        redirectTarget = IdState.Identified(
          canonicalId = sierraPictureWork.state.canonicalId,
          sourceIdentifier = sierraPictureWork.sourceIdentifier)
      )

    val expectedImage =
      metsWork.data.imageData.head.toInitialImageWith(
        modifiedTime = now,
        parentWorks = ParentWorks(
          canonicalWork = expectedMergedWork.toParentWork,
          redirectedWork = Some(metsWork.toParentWork)
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

    val expectedMergedWork = Work.Visible[Merged](
      version = sierraPhysicalWork.version,
      data = sierraPhysicalWork.data.copy(
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
      ),
      state = sierraPhysicalWork.transition[Merged](now).state,
      redirectSources = Seq(
        IdState
          .Identified(metsWork.state.canonicalId, metsWork.sourceIdentifier),
        IdState
          .Identified(miroWork.state.canonicalId, miroWork.sourceIdentifier),
        IdState.Identified(
          sierraDigitisedWork.state.canonicalId,
          sierraDigitisedWork.sourceIdentifier),
      )
    )

    val expectedRedirectedDigitalWork =
      Work.Redirected[Merged](
        state = Merged(
          canonicalId = sierraDigitisedWork.state.canonicalId,
          sourceIdentifier = sierraDigitisedWork.sourceIdentifier,
          sourceModifiedTime = sierraDigitisedWork.state.sourceModifiedTime,
          mergedTime = now
        ),
        version = sierraDigitisedWork.version,
        redirectTarget = IdState.Identified(
          canonicalId = sierraPhysicalWork.state.canonicalId,
          sourceIdentifier = sierraPhysicalWork.sourceIdentifier)
      )

    val expectedMiroRedirectedWork =
      Work.Redirected[Merged](
        state = Merged(
          canonicalId = miroWork.state.canonicalId,
          sourceIdentifier = miroWork.sourceIdentifier,
          sourceModifiedTime = miroWork.state.sourceModifiedTime,
          mergedTime = now),
        version = miroWork.version,
        redirectTarget = IdState.Identified(
          canonicalId = sierraPhysicalWork.state.canonicalId,
          sourceIdentifier = sierraPhysicalWork.sourceIdentifier)
      )

    val expectedMetsRedirectedWork =
      Work.Redirected[Merged](
        state = Merged(
          canonicalId = metsWork.state.canonicalId,
          sourceIdentifier = metsWork.sourceIdentifier,
          sourceModifiedTime = metsWork.state.sourceModifiedTime,
          mergedTime = now),
        version = metsWork.version,
        redirectTarget = IdState.Identified(
          canonicalId = sierraPhysicalWork.state.canonicalId,
          sourceIdentifier = sierraPhysicalWork.sourceIdentifier)
      )

    val expectedImage = miroWork.data.imageData.head.toInitialImageWith(
      modifiedTime = now,
      parentWorks = ParentWorks(
        canonicalWork = expectedMergedWork.toParentWork,
        redirectedWork = Some(miroWork.toParentWork)
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

    val expectedMergedWork = Work.Visible[Merged](
      version = multipleItemsSierraWork.version,
      data = multipleItemsSierraWork.data.copy(
        thumbnail = metsWork.data.thumbnail,
        items = sierraItems :+ metsItem,
      ),
      state = multipleItemsSierraWork.transition[Merged](now).state,
      redirectSources = Seq(
        IdState
          .Identified(metsWork.state.canonicalId, metsWork.sourceIdentifier)
      )
    )

    val expectedMetsRedirectedWork =
      Work.Redirected[Merged](
        state = Merged(
          canonicalId = metsWork.state.canonicalId,
          sourceIdentifier = metsWork.sourceIdentifier,
          sourceModifiedTime = metsWork.state.sourceModifiedTime,
          mergedTime = now),
        version = metsWork.version,
        redirectTarget = IdState.Identified(
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

    val expectedMergedWork = Work.Visible[Merged](
      version = multipleItemsSierraWork.version,
      data = multipleItemsSierraWork.data.copy(
        otherIdentifiers = multipleItemsSierraWork.data.otherIdentifiers ++ sierraDigitisedWork.identifiers,
        thumbnail = metsWork.data.thumbnail,
        items = sierraItems :+ metsItem,
      ),
      state = multipleItemsSierraWork.transition[Merged](now).state,
      redirectSources = Seq(
        IdState
          .Identified(metsWork.state.canonicalId, metsWork.sourceIdentifier),
        IdState.Identified(
          sierraDigitisedWork.state.canonicalId,
          sierraDigitisedWork.sourceIdentifier)
      )
    )

    val expectedRedirectedDigitalWork =
      Work.Redirected[Merged](
        state = Merged(
          canonicalId = sierraDigitisedWork.state.canonicalId,
          sourceIdentifier = sierraDigitisedWork.sourceIdentifier,
          sourceModifiedTime = sierraDigitisedWork.state.sourceModifiedTime,
          mergedTime = now
        ),
        version = sierraDigitisedWork.version,
        redirectTarget = IdState.Identified(
          canonicalId = multipleItemsSierraWork.state.canonicalId,
          sourceIdentifier = multipleItemsSierraWork.sourceIdentifier)
      )

    val expectedMetsRedirectedWork =
      Work.Redirected[Merged](
        state = Merged(
          canonicalId = metsWork.state.canonicalId,
          sourceIdentifier = metsWork.sourceIdentifier,
          sourceModifiedTime = metsWork.state.sourceModifiedTime,
          mergedTime = now),
        version = metsWork.version,
        redirectTarget = IdState.Identified(
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
    result.mergedWorksWithTime(now).head shouldBe miroWork.transition[Merged](
      now)
    result.mergedImagesWithTime(now) should have length 1
    result.mergedImagesWithTime(now).head shouldBe miroWork.data.imageData.head
      .toInitialImageWith(
        modifiedTime = now,
        parentWorks = ParentWorks(
          canonicalWork = miroWork.toParentWork,
          redirectedWork = None
        )
      )
  }

  it("doesn't merge Sierra audiovisual works") {
    val digitisedVideo =
      sierraDigitalIdentifiedWork().format(Format.EVideos)

    val physicalVideo =
      sierraPhysicalIdentifiedWork()
        .format(Format.Videos)
        .mergeCandidates(
          List(
            MergeCandidate(
              id = IdState.Identified(
                sourceIdentifier = digitisedVideo.sourceIdentifier,
                canonicalId = digitisedVideo.state.canonicalId),
              reason = "Physical/digitised Sierra work"
            )
          )
        )

    val result = merger.merge(
      works = Seq(digitisedVideo, physicalVideo)
    )

    result.resultWorks should contain theSameElementsAs Seq(
      physicalVideo,
      digitisedVideo)
  }

  it("returns both Works unmodified if one of the Works is deleted") {
    val visibleWork = identifiedWork()
    val deletedWork = identifiedWork().deleted()

    val result = merger.merge(
      works = Seq(visibleWork, deletedWork)
    )

    result.resultWorks should contain theSameElementsAs Seq(
      visibleWork,
      deletedWork)
  }

  it("merges digitised videos from METS into e-bibs") {
    // This test case is based on a real example of four related works that
    // were being merged incorrectly.  In particular, the METS work (and associated
    // IIIF manifest) was being merged into the physical video formats, not the
    // more detailed e-bib that it should have been attached to.
    //
    // See https://wellcome.slack.com/archives/C3TQSF63C/p1615474389063800
    val workWithPhysicalVideoFormats =
      sierraIdentifiedWork()
        .title("A work with physical video formats, e.g. DVD or digibeta")
        .format(Format.Film)
        .items(List(createIdentifiedPhysicalItem))

    val workForEbib =
      sierraIdentifiedWork()
        .title("A work for an e-bib")
        .format(Format.Videos)
        .mergeCandidates(
          List(
            MergeCandidate(
              id = IdState.Identified(
                canonicalId = workWithPhysicalVideoFormats.state.canonicalId,
                sourceIdentifier = workWithPhysicalVideoFormats.sourceIdentifier
              ),
              reason = "Physical/digitised Sierra work"
            )
          )
        )

    val workForMets =
      identifiedWork(sourceIdentifier = createMetsSourceIdentifier)
        .title("A METS work")
        .mergeCandidates(
          List(
            MergeCandidate(
              id = IdState.Identified(
                canonicalId = workForEbib.state.canonicalId,
                sourceIdentifier = workForEbib.sourceIdentifier
              ),
              reason = "METS work"
            )
          )
        )
        .items(List(createDigitalItem))
        .invisible()

    val workForFilmReel =
      sierraIdentifiedWork()
        .title("Work for film reel")
        .mergeCandidates(
          List(
            MergeCandidate(
              id = IdState.Identified(
                canonicalId = workForEbib.state.canonicalId,
                sourceIdentifier = workForEbib.sourceIdentifier
              ),
              reason = "Physical/digitised Sierra work"
            )
          )
        )
        .format(Format.Videos)

    val sierraWorks =
      List(workWithPhysicalVideoFormats, workForEbib, workForFilmReel)
    val works = sierraWorks :+ workForMets

    val result = merger.merge(works).mergedWorksWithTime(now)

    val visibleWorks = result
      .collect { case w: Work.Visible[_] => w }
      .map { w =>
        w.id -> w
      }
      .toMap
    val redirectedWorks = result.collect {
      case w: Work.Redirected[Merged] => w
    }
    val invisibleWorks = result.collect { case w: Work.Invisible[Merged] => w }

    // First check that the METS work got redirected into one of the Sierra works
    visibleWorks.keys should contain theSameElementsAs sierraWorks.map { _.id }
    redirectedWorks.map { _.id } shouldBe List(workForMets.id)
    invisibleWorks shouldBe empty

    // Now check that the METS work redirects into the e-bib specifically
    val redirectedWork = redirectedWorks.head
    redirectedWork.redirectTarget.canonicalId shouldBe workForEbib.state.canonicalId

    visibleWorks(workWithPhysicalVideoFormats.id).data.items shouldBe workWithPhysicalVideoFormats.data.items
    visibleWorks(workForFilmReel.id).data.items shouldBe workForFilmReel.data.items

    visibleWorks(workForEbib.id).data.items shouldBe workForMets.data.items
  }

  it("ignores online resources for physical/digital bib merging rules") {
    // This test case is based on a real example of three related works that
    // were being merged incorrectly.  In particular, the METS work (and associated
    // IIIF manifest) was being merged into the physical video formats, not the
    // more detailed e-bib that it should have been attached to.
    //
    // See https://wellcome.slack.com/archives/C8X9YKM5X/p1617705467131600
    val eVideoWork =
      sierraIdentifiedWork()
        .format(Format.Videos)
        .items(
          List(
            Item(
              id = IdState.Unidentifiable,
              title = Some("Scope: UK Disability Charity"),
              locations = List(
                DigitalLocation(
                  url = "http://www.scope.org.uk",
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
          )
        )

    val physicalVideoWork =
      sierraIdentifiedWork()
        .mergeCandidates(
          List(
            MergeCandidate(
              id = IdState.Identified(
                canonicalId = eVideoWork.state.canonicalId,
                sourceIdentifier = eVideoWork.sourceIdentifier
              ),
              reason = "Physical/digitised Sierra work"
            )
          )
        )
        .format(Format.Videos)
        .items(List(createIdentifiedPhysicalItem))

    val metsWork =
      metsIdentifiedWork()
        .mergeCandidates(
          List(
            MergeCandidate(
              id = IdState.Identified(
                canonicalId = eVideoWork.state.canonicalId,
                sourceIdentifier = eVideoWork.sourceIdentifier
              ),
              reason = "METS work"
            )
          )
        )

    val result = merger
      .merge(works = Seq(eVideoWork, physicalVideoWork, metsWork))
      .mergedWorksWithTime(now)

    val redirectedWorks = result.collect {
      case w: Work.Redirected[Merged] => w
    }
    val invisibleWorks = result.collect { case w: Work.Invisible[Merged] => w }

    invisibleWorks shouldBe empty
    redirectedWorks.map { w =>
      w.state.canonicalId -> w.redirectTarget.canonicalId
    }.toMap shouldBe Map(
      metsWork.state.canonicalId -> eVideoWork.state.canonicalId)
  }

  it("retains the 856 web link item when merging physical/digitised bibs") {
    // This test case is based on a real example, in which the links to digitised
    // journals in the Internet Archive were being added to the 856 link in the
    // digitised records.
    //
    // We don't expect digitised records to have any identified items, but if we
    // created an item from the 856 field, then we should preserve it when merging.
    //
    // See https://wellcome.slack.com/archives/C8X9YKM5X/p1621866017004000

    val item = Item(
      id = IdState.Unidentifiable,
      locations = List(
        DigitalLocation(
          url = "https://example.org/b12345678",
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

    val result = merger
      .merge(works = Seq(digitisedWork, physicalWork))
      .mergedWorksWithTime(now)

    val redirectedWorks = result.collect {
      case w: Work.Redirected[Merged] => w
    }
    val visibleWorks = result.collect { case w: Work.Visible[Merged] => w }

    redirectedWorks should have size 1
    visibleWorks should have size 1

    visibleWorks.head.data.items should contain(item)
  }
}
