package weco.pipeline.merger.services

import org.scalatest.LoneElement
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks._
import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.image.ParentWork._
import weco.catalogue.internal_model.locations._
import weco.catalogue.internal_model.work.WorkFsm._
import weco.catalogue.internal_model.work.WorkState.{Identified, Merged}
import weco.catalogue.internal_model.work.generators.SourceWorkGenerators
import weco.catalogue.internal_model.work.{
  DeletedReason,
  Format,
  InternalWork,
  InvisibilityReason,
  Item,
  Work,
  WorkData
}
import weco.pipeline.matcher.generators.MergeCandidateGenerators

class PlatformMergerTest
    extends AnyFunSpec
    with SourceWorkGenerators
    with MergeCandidateGenerators
    with LoneElement
    with Matchers {

  val digitalLocationCCBYNC = createDigitalLocationWith(
    license = Some(License.CCBYNC)
  )
  val digitalLocationNoLicense = digitalLocationCCBYNC.copy(license = None)

  val sierraDigitisedWork: Work.Visible[Identified] =
    sierraDigitalIdentifiedWork()

  val sierraPhysicalWork: Work.Visible[Identified] =
    sierraPhysicalIdentifiedWork()
      .format(Format.`3DObjects`)
      .mergeCandidates(
        List(createSierraPairMergeCandidateFor(sierraDigitisedWork))
      )

  val zeroItemSierraWork: Work.Visible[Identified] =
    sierraIdentifiedWork()
      .items(List.empty)
      .format(Format.Pictures)

  private val multipleItemsSierraWork =
    sierraIdentifiedWork()
      .items((1 to 2).map {
        _ =>
          createIdentifiedPhysicalItem
      }.toList)
      .mergeCandidates(
        List(createSierraPairMergeCandidateFor(sierraDigitisedWork))
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
    "finds Calm || Sierra with physical item || Sierra work || Nothing as a target"
  ) {
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
        "Sierra with physical item"
      ),
      (worksWithSierraTarget, Some(sierraDigitalWork), "Sierra"),
      (worksWithNoTarget, None, "Non")
    )

    forAll(examples) {
      (
        works: Seq[Work[Identified]],
        target: Option[Work.Visible[Identified]],
        clue: String
      ) =>
        withClue(clue) {
          merger.findTarget(works) should be(target)
        }
    }
  }

  it(
    "merges a Sierra picture/digital image/3D object physical work with a Miro work"
  ) {
    val result = merger.merge(
      works = Seq(sierraPhysicalWork, miroWork)
    )

    result.mergedWorksWithTime(now).size shouldBe 2

    val sierraItem = sierraPhysicalWork.data.items.head
    val miroItem = miroWork.data.items.head

    val expectedMergedWork = Work.Visible[Merged](
      version = sierraPhysicalWork.version,
      data = sierraPhysicalWork.data.copy(
        otherIdentifiers =
          sierraPhysicalWork.data.otherIdentifiers ++ miroWork.identifiers,
        thumbnail = miroWork.data.thumbnail,
        items = List(
          sierraItem.copy(
            locations = sierraItem.locations ++ miroItem.locations
          )
        ),
        imageData = miroWork.data.imageData
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
          sourceIdentifier = sierraPhysicalWork.sourceIdentifier
        )
      )

    val expectedImage =
      miroWork.data.imageData.head.toInitialImageWith(
        modifiedTime = now,
        parentWork = expectedMergedWork.toParentWork
      )
    result.mergedWorksWithTime(now) should contain theSameElementsAs List(
      expectedMergedWork,
      expectedRedirectedWork
    )
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
        otherIdentifiers =
          zeroItemSierraWork.data.otherIdentifiers ++ miroWork.identifiers,
        thumbnail = miroWork.data.thumbnail,
        items = miroWork.data.items,
        imageData = miroWork.data.imageData
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
          mergedTime = now
        ),
        version = miroWork.version,
        redirectTarget = IdState.Identified(
          canonicalId = zeroItemSierraWork.state.canonicalId,
          sourceIdentifier = zeroItemSierraWork.sourceIdentifier
        )
      )

    val expectedImage =
      miroWork.data.imageData.head.toInitialImageWith(
        modifiedTime = now,
        parentWork = expectedMergedWork.toParentWork
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
    "merges a Sierra picture/digital image/3D object digital work with a Miro work"
  ) {
    val result = merger.merge(
      works = Seq(sierraDigitalWork, miroWork)
    )

    result.mergedWorksWithTime(now).size shouldBe 2

    val sierraItem = sierraDigitalWork.data.items.head
    val miroItem = miroWork.data.items.head

    val expectedMergedWork = Work.Visible[Merged](
      version = sierraDigitalWork.version,
      data = sierraDigitalWork.data.copy(
        otherIdentifiers =
          sierraDigitalWork.data.otherIdentifiers ++ miroWork.identifiers,
        thumbnail = miroWork.data.thumbnail,
        items = List(
          sierraItem.copy(
            locations = sierraItem.locations ++ miroItem.locations
          )
        ),
        imageData = miroWork.data.imageData
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
          mergedTime = now
        ),
        version = miroWork.version,
        redirectTarget = IdState.Identified(
          canonicalId = sierraDigitalWork.state.canonicalId,
          sourceIdentifier = sierraDigitalWork.sourceIdentifier
        )
      )

    val expectedImage = miroWork.data.imageData.head.toInitialImageWith(
      modifiedTime = now,
      parentWork = expectedMergedWork.toParentWork
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
    "does not merge a sierra work with multiple items with a linked Miro work"
  ) {
    val result = merger.merge(
      works = Seq(multipleItemsSierraWork, miroWork)
    )

    result.mergedWorksWithTime(now).size shouldBe 2

    val expectedMergedWork = Work.Visible[Merged](
      version = multipleItemsSierraWork.version,
      data = multipleItemsSierraWork.data.copy(
        imageData = miroWork.data.imageData
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
        mergedTime = now
      ),
      version = miroWork.version,
      redirectTarget = IdState.Identified(
        canonicalId = multipleItemsSierraWork.state.canonicalId,
        sourceIdentifier = multipleItemsSierraWork.sourceIdentifier
      )
    )

    result.mergedWorksWithTime(now) should contain theSameElementsAs Seq(
      expectedRedirectedMiro,
      expectedMergedWork
    )
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
        thumbnail = metsWork.data.thumbnail
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
          mergedTime = now
        ),
        version = metsWork.version,
        redirectTarget = IdState.Identified(
          canonicalId = sierraPhysicalWork.state.canonicalId,
          sourceIdentifier = sierraPhysicalWork.sourceIdentifier
        )
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
        deletedWork.transition[Merged](now)
      )

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
        imageData = Nil,
        thumbnail = metsWork.data.thumbnail
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
          mergedTime = now
        ),
        version = metsWork.version,
        redirectTarget = IdState.Identified(
          canonicalId = sierraPictureWork.state.canonicalId,
          sourceIdentifier = sierraPictureWork.sourceIdentifier
        )
      )

    val expectedImage =
      metsWork.data.imageData.head.toInitialImageWith(
        modifiedTime = now,
        parentWork = expectedMergedWork.toParentWork
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
    "merges a 3D object physical Sierra work with a digital Sierra work, a Miro work and a METS work"
  ) {
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
        imageData = miroWork.data.imageData
      ),
      state = sierraPhysicalWork.transition[Merged](now).state,
      redirectSources = Seq(
        IdState
          .Identified(metsWork.state.canonicalId, metsWork.sourceIdentifier),
        IdState
          .Identified(miroWork.state.canonicalId, miroWork.sourceIdentifier),
        IdState.Identified(
          sierraDigitisedWork.state.canonicalId,
          sierraDigitisedWork.sourceIdentifier
        )
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
          sourceIdentifier = sierraPhysicalWork.sourceIdentifier
        )
      )

    val expectedMiroRedirectedWork =
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
          sourceIdentifier = sierraPhysicalWork.sourceIdentifier
        )
      )

    val expectedMetsRedirectedWork =
      Work.Redirected[Merged](
        state = Merged(
          canonicalId = metsWork.state.canonicalId,
          sourceIdentifier = metsWork.sourceIdentifier,
          sourceModifiedTime = metsWork.state.sourceModifiedTime,
          mergedTime = now
        ),
        version = metsWork.version,
        redirectTarget = IdState.Identified(
          canonicalId = sierraPhysicalWork.state.canonicalId,
          sourceIdentifier = sierraPhysicalWork.sourceIdentifier
        )
      )

    val expectedImage = miroWork.data.imageData.head.toInitialImageWith(
      modifiedTime = now,
      parentWork = expectedMergedWork.toParentWork
    )

    result.mergedWorksWithTime(now) should contain theSameElementsAs List(
      expectedMergedWork,
      expectedRedirectedDigitalWork,
      expectedMiroRedirectedWork,
      expectedMetsRedirectedWork
    )
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
        items = sierraItems :+ metsItem
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
          mergedTime = now
        ),
        version = metsWork.version,
        redirectTarget = IdState.Identified(
          canonicalId = multipleItemsSierraWork.state.canonicalId,
          sourceIdentifier = multipleItemsSierraWork.sourceIdentifier
        )
      )

    result.mergedWorksWithTime(now) should contain theSameElementsAs List(
      expectedMergedWork,
      expectedMetsRedirectedWork
    )
    result.mergedImagesWithTime(now) shouldBe empty
  }

  it(
    "merges a multiple items physical Sierra work with a digital Sierra work and a METS work"
  ) {
    val result = merger.merge(
      works = Seq(multipleItemsSierraWork, sierraDigitisedWork, metsWork)
    )

    result.mergedWorksWithTime(now).size shouldBe 3

    val sierraItems = multipleItemsSierraWork.data.items
    val metsItem = metsWork.data.items.head

    val expectedMergedWork = Work.Visible[Merged](
      version = multipleItemsSierraWork.version,
      data = multipleItemsSierraWork.data.copy(
        otherIdentifiers =
          multipleItemsSierraWork.data.otherIdentifiers ++ sierraDigitisedWork.identifiers,
        thumbnail = metsWork.data.thumbnail,
        items = sierraItems :+ metsItem
      ),
      state = multipleItemsSierraWork.transition[Merged](now).state,
      redirectSources = Seq(
        IdState
          .Identified(metsWork.state.canonicalId, metsWork.sourceIdentifier),
        IdState.Identified(
          sierraDigitisedWork.state.canonicalId,
          sierraDigitisedWork.sourceIdentifier
        )
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
          sourceIdentifier = multipleItemsSierraWork.sourceIdentifier
        )
      )

    val expectedMetsRedirectedWork =
      Work.Redirected[Merged](
        state = Merged(
          canonicalId = metsWork.state.canonicalId,
          sourceIdentifier = metsWork.sourceIdentifier,
          sourceModifiedTime = metsWork.state.sourceModifiedTime,
          mergedTime = now
        ),
        version = metsWork.version,
        redirectTarget = IdState.Identified(
          canonicalId = multipleItemsSierraWork.state.canonicalId,
          sourceIdentifier = multipleItemsSierraWork.sourceIdentifier
        )
      )

    result.mergedWorksWithTime(now) should contain theSameElementsAs List(
      expectedMergedWork,
      expectedRedirectedDigitalWork,
      expectedMetsRedirectedWork
    )

    result.mergedImagesWithTime(now) shouldBe empty
  }

  it("creates an image for a single Miro target") {
    val result = merger.merge(List(miroWork))

    result.mergedWorksWithTime(now) should have length 1
    result.mergedWorksWithTime(now).head shouldBe miroWork.transition[Merged](
      now
    )
    result.mergedImagesWithTime(now) should have length 1
    result.mergedImagesWithTime(now).head shouldBe miroWork.data.imageData.head
      .toInitialImageWith(
        modifiedTime = now,
        parentWork = miroWork.toParentWork
      )
  }

  it("doesn't merge Sierra audiovisual works") {
    val digitisedVideo =
      sierraDigitalIdentifiedWork().format(Format.EVideos)

    val physicalVideo =
      sierraPhysicalIdentifiedWork()
        .format(Format.Videos)
        .mergeCandidates(
          List(createSierraPairMergeCandidateFor(digitisedVideo))
        )

    val result = merger.merge(
      works = Seq(digitisedVideo, physicalVideo)
    )

    result.resultWorks should contain theSameElementsAs Seq(
      physicalVideo,
      digitisedVideo
    )
  }

  it("returns both Works unmodified if one of the Works is deleted") {
    val visibleWork = identifiedWork()
    val deletedWork = identifiedWork().deleted()

    val result = merger.merge(
      works = Seq(visibleWork, deletedWork)
    )

    result.resultWorks should contain theSameElementsAs Seq(
      visibleWork,
      deletedWork
    )
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
          List(createSierraPairMergeCandidateFor(workWithPhysicalVideoFormats))
        )

    val workForMets =
      identifiedWork(sourceIdentifier = createMetsSourceIdentifier)
        .title("A METS work")
        .mergeCandidates(List(createMetsMergeCandidateFor(workForEbib)))
        .items(List(createDigitalItem))
        .invisible()

    val workForFilmReel =
      sierraIdentifiedWork()
        .title("Work for film reel")
        .mergeCandidates(
          List(createSierraPairMergeCandidateFor(workForEbib))
        )
        .format(Format.Videos)

    val sierraWorks =
      List(workWithPhysicalVideoFormats, workForEbib, workForFilmReel)
    val works = sierraWorks :+ workForMets

    val result = merger.merge(works).mergedWorksWithTime(now)

    val visibleWorks = result
      .collect { case w: Work.Visible[_] => w }
      .map {
        w =>
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

    visibleWorks(
      workWithPhysicalVideoFormats.id
    ).data.items shouldBe workWithPhysicalVideoFormats.data.items
    visibleWorks(
      workForFilmReel.id
    ).data.items shouldBe workForFilmReel.data.items

    visibleWorks(workForEbib.id).data.items shouldBe workForMets.data.items
  }

  it("merges digitised audio from METS into e-bibs") {
    // This test case is based on a real example of three related works that
    // were being merged incorrectly.  In particular, the METS work (and associated
    // IIIF manifest) was being merged into the physical audio formats, not the
    // e-bib that it should have been attached to.
    //
    //
    // See https://wellcome.slack.com/archives/C8X9YKM5X/p1668592141214869?thread_ts=1668438141.675609&cid=C8X9YKM5X
    val workForPhysicalCassette = sierraIdentifiedWork()
      .title("A physical cassette tape")
      .format(Format.Audio)
      .items(List(createIdentifiedPhysicalItem))

    val workForEbib = sierraIdentifiedWork()
      .title("A work for an e-bib")
      .format(Format.Audio)

    val workForMets =
      identifiedWork(sourceIdentifier = createMetsSourceIdentifier)
        .title("The digital copy of the audio cassette")
        .items(List(createDigitalItem))
        .invisible()

    val sierraWorks = List(workForPhysicalCassette, workForEbib)
    val works = List(workForPhysicalCassette, workForEbib, workForMets)

    val result = merger.merge(works).mergedWorksWithTime(now)

    val visibleWorks = result
      .collect { case w: Work.Visible[_] => w }
      .map {
        w =>
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

    visibleWorks(
      workForPhysicalCassette.id
    ).data.items shouldBe workForPhysicalCassette.data.items
    visibleWorks(workForEbib.id).data.items shouldBe workForMets.data.items
  }

  it("still merges a METS work that names the physical bib into the e-bib") {
    val workForPhysicalCassette = sierraIdentifiedWork()
      .format(Format.Audio)
      .items(List(createIdentifiedPhysicalItem))

    val workForEbib = sierraIdentifiedWork()
      .format(Format.Audio)
      .mergeCandidates(
        List(createSierraPairMergeCandidateFor(workForPhysicalCassette))
      )

    val workForMets =
      identifiedWork(sourceIdentifier = createMetsSourceIdentifier)
        .mergeCandidates(
          List(createMetsMergeCandidateFor(workForPhysicalCassette))
        )
        .items(List(createDigitalItem))
        .invisible()

    val result = merger
      .merge(List(workForPhysicalCassette, workForEbib, workForMets))
      .mergedWorksWithTime(now)

    val redirected = result.collect { case w: Work.Redirected[Merged] => w }
    redirected.map(_.id) shouldBe List(workForMets.id)
    redirected.head.redirectTarget.canonicalId shouldBe workForEbib.state.canonicalId
  }

  it(
    "merges a METS work that names the physical bib into it once the e-bibs are carved out"
  ) {
    val physical = sierraIdentifiedWork()
      .format(Format.Audio)
      .items(List(createIdentifiedPhysicalItem))
    val ebibs = (1 to 2).map {
      _ =>
        sierraIdentifiedWork()
          .format(Format.Audio)
          .mergeCandidates(List(createSierraPairMergeCandidateFor(physical)))
    }.toList
    val metsForEbibs = ebibs.map {
      ebib =>
        identifiedWork(sourceIdentifier = createMetsSourceIdentifier)
          .mergeCandidates(List(createMetsMergeCandidateFor(ebib)))
          .items(List(createDigitalItem))
          .invisible()
    }
    val metsForPhysical =
      identifiedWork(sourceIdentifier = createMetsSourceIdentifier)
        .mergeCandidates(List(createMetsMergeCandidateFor(physical)))
        .items(List(createDigitalItem))
        .invisible()

    val result = merger
      .merge(physical :: ebibs ++ metsForEbibs :+ metsForPhysical)
      .mergedWorksWithTime(now)

    val redirects = result
      .collect { case w: Work.Redirected[Merged] => w }
      .map(w => w.id -> w.redirectTarget.canonicalId)
      .toMap
    redirects shouldBe Map(
      metsForEbibs(0).id -> ebibs(0).state.canonicalId,
      metsForEbibs(1).id -> ebibs(1).state.canonicalId,
      metsForPhysical.id -> physical.state.canonicalId
    )
    result
      .collect { case w: Work.Visible[Merged] if w.id == physical.id => w }
      .head
      .data
      .items
      .flatMap(_.locations) should contain theSameElementsAs
      physical.data.items.flatMap(_.locations) ++ metsForPhysical.data.items
        .flatMap(_.locations)
  }

  it(
    "redirects a METS work linked to several audiovisual e-bibs to the e-bib that was not carved out"
  ) {
    val physical = sierraIdentifiedWork()
      .format(Format.Audio)
      .items(List(createIdentifiedPhysicalItem))
    val ebibs = (1 to 2).map {
      _ =>
        sierraIdentifiedWork()
          .format(Format.Audio)
          .mergeCandidates(List(createSierraPairMergeCandidateFor(physical)))
    }.toList
    val ambiguousMets =
      identifiedWork(sourceIdentifier = createMetsSourceIdentifier)
        .mergeCandidates(ebibs.map(createMetsMergeCandidateFor))
        .items(List(createDigitalItem))
        .invisible()
    val metsForFirstEbib =
      identifiedWork(sourceIdentifier = createMetsSourceIdentifier)
        .mergeCandidates(List(createMetsMergeCandidateFor(ebibs(0))))
        .items(List(createDigitalItem))
        .invisible()

    val result = merger
      .merge(physical :: ebibs ++ List(ambiguousMets, metsForFirstEbib))
      .mergedWorksWithTime(now)

    val redirects = result
      .collect { case w: Work.Redirected[Merged] => w }
      .map(w => w.id -> w.redirectTarget.canonicalId)
      .toMap
    redirects(metsForFirstEbib.id) shouldBe ebibs(0).state.canonicalId
    redirects(ambiguousMets.id) shouldBe ebibs(1).state.canonicalId
  }

  it(
    "redirects a METS work linked to several audiovisual e-bibs to the physical bib once every e-bib is carved out"
  ) {
    val physical = sierraIdentifiedWork()
      .format(Format.Audio)
      .items(List(createIdentifiedPhysicalItem))
    val ebibs = (1 to 2).map {
      _ =>
        sierraIdentifiedWork()
          .format(Format.Audio)
          .mergeCandidates(List(createSierraPairMergeCandidateFor(physical)))
    }.toList
    val metsForEbibs = ebibs.map {
      ebib =>
        identifiedWork(sourceIdentifier = createMetsSourceIdentifier)
          .mergeCandidates(List(createMetsMergeCandidateFor(ebib)))
          .items(List(createDigitalItem))
          .invisible()
    }
    val ambiguousMets =
      identifiedWork(sourceIdentifier = createMetsSourceIdentifier)
        .mergeCandidates(ebibs.map(createMetsMergeCandidateFor))
        .items(List(createDigitalItem))
        .invisible()

    val result = merger
      .merge(physical :: ebibs ++ metsForEbibs :+ ambiguousMets)
      .mergedWorksWithTime(now)

    val redirects = result
      .collect { case w: Work.Redirected[Merged] => w }
      .map(w => w.id -> w.redirectTarget.canonicalId)
      .toMap
    redirects shouldBe Map(
      metsForEbibs(0).id -> ebibs(0).state.canonicalId,
      metsForEbibs(1).id -> ebibs(1).state.canonicalId,
      ambiguousMets.id -> physical.state.canonicalId
    )
  }

  it("gives each audiovisual e-bib its own METS work") {
    // Based on a real example: a two-sided audio cassette catalogued as one
    // physical bib and two e-bibs, both linking to the physical bib via 776.
    // Both METS works were being attached to whichever e-bib was elected as
    // the cluster target, leaving the other e-bib with no manifest.
    //
    // See https://github.com/wellcomecollection/platform/issues/6643
    val workForPhysicalCassette = sierraIdentifiedWork()
      .title("A physical cassette tape")
      .format(Format.Audio)
      .items(List(createIdentifiedPhysicalItem))

    val workForSideA = sierraIdentifiedWork()
      .title("Side A")
      .format(Format.Audio)
      .items(
        List(
          createUnidentifiableItemWith(locations = List(createDigitalLocation))
        )
      )
      .mergeCandidates(
        List(createSierraPairMergeCandidateFor(workForPhysicalCassette))
      )

    val workForSideB = sierraIdentifiedWork()
      .title("Side B")
      .format(Format.Audio)
      .mergeCandidates(
        List(createSierraPairMergeCandidateFor(workForPhysicalCassette))
      )

    val metsForSideA =
      identifiedWork(sourceIdentifier = createMetsSourceIdentifier)
        .mergeCandidates(List(createMetsMergeCandidateFor(workForSideA)))
        .items(List(createDigitalItem))
        .invisible()

    val metsForSideB =
      identifiedWork(sourceIdentifier = createMetsSourceIdentifier)
        .mergeCandidates(List(createMetsMergeCandidateFor(workForSideB)))
        .items(List(createDigitalItem))
        .invisible()

    val sierraWorks = List(workForPhysicalCassette, workForSideA, workForSideB)

    // The outcome must not depend on the order the works arrive in
    forAll(
      Table(
        "works",
        sierraWorks ++ List(metsForSideA, metsForSideB),
        List(
          metsForSideB,
          workForSideB,
          workForSideA,
          metsForSideA,
          workForPhysicalCassette
        )
      )
    ) {
      works =>
        val result = merger.merge(works).mergedWorksWithTime(now)

        val visibleWorks = result
          .collect { case w: Work.Visible[_] => w }
          .map(w => w.id -> w)
          .toMap
        val redirectedWorks = result
          .collect { case w: Work.Redirected[Merged] => w }
          .map(w => w.id -> w.redirectTarget.canonicalId)
          .toMap

        visibleWorks.keys should contain theSameElementsAs sierraWorks.map(_.id)
        redirectedWorks shouldBe Map(
          metsForSideA.id -> workForSideA.state.canonicalId,
          metsForSideB.id -> workForSideB.state.canonicalId
        )

        visibleWorks(
          workForPhysicalCassette.id
        ).data.items shouldBe workForPhysicalCassette.data.items
        visibleWorks(workForSideA.id).data.items shouldBe
          List(
            workForSideA.data.items.head.copy(
              locations =
                workForSideA.data.items.head.locations ++ metsForSideA.data.items.head.locations
            )
          )
        visibleWorks(
          workForSideB.id
        ).data.items shouldBe metsForSideB.data.items
    }
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
          List(createSierraPairMergeCandidateFor(eVideoWork))
        )
        .format(Format.Videos)
        .items(List(createIdentifiedPhysicalItem))

    val metsWork =
      metsIdentifiedWork()
        .mergeCandidates(List(createMetsMergeCandidateFor(eVideoWork)))

    val result = merger
      .merge(works = Seq(eVideoWork, physicalVideoWork, metsWork))
      .mergedWorksWithTime(now)

    val redirectedWorks = result.collect {
      case w: Work.Redirected[Merged] => w
    }
    val invisibleWorks = result.collect { case w: Work.Invisible[Merged] => w }

    invisibleWorks shouldBe empty
    redirectedWorks.map {
      w =>
        w.state.canonicalId -> w.redirectTarget.canonicalId
    }.toMap shouldBe Map(
      metsWork.state.canonicalId -> eVideoWork.state.canonicalId
    )
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
          List(createSierraPairMergeCandidateFor(digitisedWork))
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

  it(
    "preserves the identifiers when it merges a Sierra bib, e-bib and METS work and the e-bib has the link"
  ) {
    // This test case is based on a real issue, when identifiers weren't being copied
    // across correctly and we were losing identifiers in the merging process.

    val physicalWork =
      sierraIdentifiedWork()
        .otherIdentifiers(
          List(createSierraIdentifierSourceIdentifier)
        )
        .format(Format.Books)
        .items(List(createIdentifiedPhysicalItem))

    val electronicWork =
      sierraIdentifiedWork()
        .otherIdentifiers(
          List(
            createSierraIdentifierSourceIdentifier,
            createDigcodeIdentifier("digsexology")
          )
        )
        .format(Format.Books)
        .mergeCandidates(
          List(createSierraPairMergeCandidateFor(physicalWork))
        )

    val metsWork =
      metsIdentifiedWork()
        .mergeCandidates(List(createMetsMergeCandidateFor(electronicWork)))
        .items(List(createDigitalItem))
        .invisible(List(InvisibilityReason.MetsWorksAreNotVisible))

    val works = Seq(metsWork, electronicWork, physicalWork)

    val redirectedWork = merger
      .merge(works)
      .mergedWorksWithTime(now)
      .collectFirst { case w: Work.Visible[Merged] => w }
      .get

    redirectedWork.state.canonicalId shouldBe physicalWork.state.canonicalId
    redirectedWork.identifiers should contain theSameElementsAs (physicalWork.identifiers ++ electronicWork.identifiers)
  }

  it(
    "handles a Sierra physical/digitised pair where there's an irrelevant Miro work in the mix"
  ) {
    // This is a regression test for an issue we saw where the identifiers were
    // being dropped from a set of works where:
    //
    //    Sierra e-bib
    //        ↓
    //    Sierra physical bib
    //        ↓
    //    Miro work
    //
    // and the identifiers were being dropped from the merged work.
    //
    // See https://wellcome.slack.com/archives/C8X9YKM5X/p1645180445345339

    val miroWork = miroIdentifiedWork()

    val physicalWork =
      sierraPhysicalIdentifiedWork()
        .format(Format.Books)
        .mergeCandidates(
          List(createMiroSierraMergeCandidateFor(miroWork))
        )

    val digitisedWork =
      sierraDigitalIdentifiedWork()
        .mergeCandidates(
          List(createSierraPairMergeCandidateFor(physicalWork))
        )

    val result =
      merger.merge(works = Seq(miroWork, physicalWork, digitisedWork))

    val mergedWork =
      result
        .mergedWorksWithTime(now)
        .collectFirst { case w: Work.Visible[Merged] => w }
        .get

    mergedWork.identifiers should contain theSameElementsAs (
      physicalWork.identifiers ++ digitisedWork.identifiers
    )
  }

  describe("merging EBSCO & Sierra works") {
    it("merges a Sierra digital work with an EBSCO work") {
      val merger = PlatformMerger
      val (sierraDigitalWork, ebscoWork) = sierraEbscoIdentifiedWorkPair()

      val result = merger
        .merge(works = Seq(sierraDigitalWork, ebscoWork))
        .mergedWorksWithTime(now)
      val redirectedWorks = result.collect {
        case w: Work.Redirected[Merged] => w
      }
      val visibleWorks = result.collect { case w: Work.Visible[Merged] => w }

      redirectedWorks should have size 1
      visibleWorks should have size 1

      visibleWorks.loneElement.state.canonicalId shouldBe ebscoWork.state.canonicalId
    }
  }

  describe("merging TEI works") {
    it("merges a physical sierra with a tei") {
      val merger = PlatformMerger
      val physicalWork =
        sierraIdentifiedWork()
          .items(List(createIdentifiedPhysicalItem))
      val teiWork = teiIdentifiedWork()
        .mergeCandidates(List(createSierraPairMergeCandidateFor(physicalWork)))

      val result = merger
        .merge(works = Seq(teiWork, physicalWork))
        .mergedWorksWithTime(now)
      val redirectedWorks = result.collect {
        case w: Work.Redirected[Merged] => w
      }
      val visibleWorks = result.collect { case w: Work.Visible[Merged] => w }

      redirectedWorks should have size 1
      visibleWorks should have size 1

      visibleWorks.loneElement.state.canonicalId shouldBe teiWork.state.canonicalId
    }

    // A manuscript can be catalogued as several Calm records, each with its
    // own Sierra bib, all describing what is one TEI file. The TEI file names
    // every one of those bibs, so the whole group should collapse onto it.
    it("merges several Sierra bibs and their Calm records into one TEI work") {
      val sierraWorks = (1 to 3).map(_ => sierraPhysicalIdentifiedWork()).toList
      val calmWorks = (1 to 3).map(_ => calmIdentifiedWork()).toList

      val teiWork = teiIdentifiedWork()
        .mergeCandidates(sierraWorks.map(createTeiBnumberMergeCandidateFor))

      val result = merger
        .merge(works = teiWork +: (sierraWorks ++ calmWorks))
        .mergedWorksWithTime(now)

      val redirectedWorks = result.collect {
        case w: Work.Redirected[Merged] => w
      }
      val visibleWorks = result.collect { case w: Work.Visible[Merged] => w }

      visibleWorks.loneElement.state.canonicalId shouldBe teiWork.state.canonicalId

      redirectedWorks.map(_.state.canonicalId) should contain theSameElementsAs
        (sierraWorks ++ calmWorks).map(_.state.canonicalId)
      redirectedWorks.map(_.redirectTarget.canonicalId).distinct shouldBe
        List(teiWork.state.canonicalId)
    }

    it("takes the items from every Sierra bib merged into a TEI work") {
      val sierraWorks = (1 to 3).map(_ => sierraPhysicalIdentifiedWork()).toList

      val teiWork = teiIdentifiedWork()
        .mergeCandidates(sierraWorks.map(createTeiBnumberMergeCandidateFor))

      val result = merger
        .merge(works = teiWork +: sierraWorks)
        .mergedWorksWithTime(now)

      val visibleWorks = result.collect { case w: Work.Visible[Merged] => w }

      visibleWorks.loneElement.data.items should contain theSameElementsAs
        sierraWorks.flatMap(_.data.items)
    }

    it("copies the thumbnail to the inner works") {
      val teiWork = teiIdentifiedWork()
        .mapState {
          _.copy(
            internalWorkStubs = List(
              InternalWork.Identified(
                sourceIdentifier = createTeiSourceIdentifier,
                canonicalId = createCanonicalId,
                workData = WorkData(
                  title = Some(s"tei-inner-${randomAlphanumeric(length = 10)}")
                )
              )
            )
          )
        }

      val sierraWork = sierraPhysicalIdentifiedWork()

      val metsWork = metsIdentifiedWork()
        .thumbnail(createDigitalLocation)

      val result =
        PlatformMerger
          .merge(works = Seq(teiWork, sierraWork, metsWork))
          .mergedWorksWithTime(now)

      val visibleWorks = result.collect { case w: Work.Visible[Merged] => w }

      visibleWorks.foreach {
        _.data.thumbnail shouldBe metsWork.data.thumbnail
      }
    }

    it("deletes the inner works of a deleted TEI work") {
      val stubs = List(createInternalWorkStub, createInternalWorkStub)
      val deletedTeiWork = teiIdentifiedWork()
        .mapState { _.copy(internalWorkStubs = stubs) }
        .deleted()

      val result = merger.merge(works = Seq(deletedTeiWork))

      result.resultWorks should have size 3

      val deletedWorks = result.resultWorks.collect {
        case w: Work.Deleted[Identified] => w
      }
      deletedWorks.map(_.state.canonicalId) should contain theSameElementsAs
        deletedTeiWork.state.canonicalId +: stubs.map(_.canonicalId)

      val innerWorks =
        deletedWorks.filterNot(
          _.state.canonicalId == deletedTeiWork.state.canonicalId
        )
      innerWorks.foreach {
        w =>
          w.deletedReason shouldBe DeletedReason.TeiDeletedInMerger
          w.version shouldBe deletedTeiWork.version
          w.state.sourceModifiedTime shouldBe deletedTeiWork.state.sourceModifiedTime
      }
    }

    it(
      "deletes the inner works of a deleted TEI work alongside a visible target"
    ) {
      val stubs = List(createInternalWorkStub, createInternalWorkStub)
      val deletedTeiWork = teiIdentifiedWork()
        .mapState { _.copy(internalWorkStubs = stubs) }
        .deleted()

      val sierraWork = sierraPhysicalIdentifiedWork()

      val result = merger.merge(works = Seq(sierraWork, deletedTeiWork))

      result.resultWorks should have size 4

      result.resultWorks.collect {
        case w: Work.Visible[Identified] => w.state.canonicalId
      } shouldBe Seq(sierraWork.state.canonicalId)

      result.resultWorks.collect {
        case w: Work.Deleted[Identified] => w.state.canonicalId
      } should contain theSameElementsAs
        deletedTeiWork.state.canonicalId +: stubs.map(_.canonicalId)
    }

    it("deletes an inner work whose stub has been removed from the record") {
      val kept = createInternalWorkStub
      val removed = createInternalWorkStub
      val teiWork = teiIdentifiedWork().mapState {
        _.copy(
          internalWorkStubs = List(kept),
          removedInternalWorkStubs = List(removed)
        )
      }

      val result = merger.merge(works = Seq(teiWork))

      result.resultWorks.collect {
        case w: Work.Visible[Identified] => w.state.canonicalId
      } should contain theSameElementsAs Seq(
        teiWork.state.canonicalId,
        kept.canonicalId
      )

      val deleted = result.resultWorks.collect {
        case w: Work.Deleted[Identified] => w
      }
      deleted.map(_.state.canonicalId) shouldBe Seq(removed.canonicalId)
      deleted.head.deletedReason shouldBe DeletedReason.TeiDeletedInMerger
    }

    it("deletes removed inner works of a TEI work that loses the merge") {
      // An EBSCO work outranks TEI, so the TEI work is redirected and its
      // stubs are dropped from the result. The removals still have to happen.
      val removed = createInternalWorkStub
      val teiWork = teiIdentifiedWork().mapState {
        _.copy(removedInternalWorkStubs = List(removed))
      }
      val ebscoWork = ebscoIdentifiedWork()

      val result = merger.merge(works = Seq(ebscoWork, teiWork))

      result.resultWorks.collect {
        case w: Work.Deleted[Identified] => w.state.canonicalId
      } shouldBe Seq(removed.canonicalId)
    }

    it("never deletes an inner work it has just emitted") {
      // The transformer drops a stub from the removed list when the record
      // takes it back, so the two lists should not overlap. If they ever do,
      // the live work has to win over the delete.
      val contested = createInternalWorkStub
      val teiWork = teiIdentifiedWork().mapState {
        _.copy(
          internalWorkStubs = List(contested),
          removedInternalWorkStubs = List(contested)
        )
      }

      val result = merger.merge(works = Seq(teiWork))

      result.resultWorks.collect {
        case w: Work.Deleted[Identified] => w.state.canonicalId
      } shouldBe empty
      result.resultWorks.collect {
        case w: Work.Visible[Identified] => w.state.canonicalId
      } should contain(contested.canonicalId)
    }
  }

  it("passes a Folio work through the merger unchanged") {
    val folioWork = identifiedWork(
      sourceIdentifier = createFolioSourceIdentifier
    )

    val result = merger.merge(works = Seq(folioWork))

    val mergedWorks = result.mergedWorksWithTime(now)
    mergedWorks.size shouldBe 1

    val mergedWork = mergedWorks.head.asInstanceOf[Work.Visible[Merged]]
    mergedWork.data shouldBe folioWork.data
  }

  it("passes an Axiell work through the merger unchanged") {
    val axiellWork = identifiedWork(
      sourceIdentifier = createAxiellSourceIdentifier
    )

    val result = merger.merge(works = Seq(axiellWork))

    val mergedWorks = result.mergedWorksWithTime(now)
    mergedWorks.size shouldBe 1

    val mergedWork = mergedWorks.head.asInstanceOf[Work.Visible[Merged]]
    mergedWork.data shouldBe axiellWork.data
  }

  private def createInternalWorkStub: InternalWork.Identified =
    InternalWork.Identified(
      sourceIdentifier = createTeiSourceIdentifier,
      canonicalId = createCanonicalId,
      workData = WorkData(
        title = Some(s"tei-inner-${randomAlphanumeric(length = 10)}")
      )
    )
}
