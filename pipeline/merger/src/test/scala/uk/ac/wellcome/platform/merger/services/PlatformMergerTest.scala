package uk.ac.wellcome.platform.merger.services

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.models.work.generators.WorksGenerators
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.merger.fixtures.ImageFulltextAccess
import org.scalatest.prop.TableDrivenPropertyChecks._

class PlatformMergerTest
    extends AnyFunSpec
    with WorksGenerators
    with Matchers
    with ImageFulltextAccess {
  val digitalLocationCCBYNC = createDigitalLocationWith(
    license = Some(License.CCBYNC))
  val digitalLocationNoLicense = digitalLocationCCBYNC.copy(license = None)

  val sierraDigitised = createUnidentifiedSierraWork
  val sierraPhysicalWork = createSierraPhysicalWork.copy(
    data = createSierraPhysicalWork.data.copy(
      mergeCandidates = List(
        MergeCandidate(
          sierraDigitised.sourceIdentifier,
          Some("Physical/digitised Sierra work"))),
      workType = Some(WorkType.`3DObjects`)
    ))
  private val multipleItemsSierraWork =
    createSierraWorkWithTwoPhysicalItems.copy(
      data = createSierraWorkWithTwoPhysicalItems.data.copy(
        mergeCandidates = List(
          MergeCandidate(
            sierraDigitised.sourceIdentifier,
            Some("Physical/digitised Sierra work")))
      ))
  private val sierraDigitalWork = createUnidentifiedSierraWorkWith(
    items = List(createDigitalItemWith(List(digitalLocationNoLicense))),
    workType = Some(WorkType.DigitalImages)
  )
  private val sierraPictureWork = createUnidentifiedSierraWorkWith(
    items = List(createPhysicalItem),
    workType = Some(WorkType.Pictures)
  )
  private val miroWork = createMiroWorkWith(
    sourceIdentifier = createNonHistoricalLibraryMiroSourceIdentifier
  )
  private val historicalLibraryMiroWork = createMiroWorkWith(
    sourceIdentifier = createHistoricalLibraryMiroSourceIdentifier
  )
  private val metsWork =
    createUnidentifiedInvisibleMetsWorkWith(
      items = List(createDigitalItemWith(List(digitalLocationCCBYNC)))
    ).withData { data =>
      data.copy(
        thumbnail = Some(
          DigitalLocation(
            url = "https://path.to/thumbnail.jpg",
            locationType = LocationType("thumbnail-image"),
            license = Some(License.CCBY)
          )
        )
      )
    }
  val calmWork = createUnidentifiedCalmWork

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
      (works: Seq[TransformedBaseWork],
       target: Option[UnidentifiedWork],
       clue: String) =>
        withClue(clue) {
          merger.findTarget(works) should be(target)
        }
    }
  }

  it(
    "merges a Sierra picture/digital image/3D object physical work with a non-historical-library Miro work") {
    val result = merger.merge(
      works = Seq(sierraPhysicalWork, miroWork)
    )

    result.works.size shouldBe 2

    val sierraItem = sierraPhysicalWork.data.items.head
    val miroItem = miroWork.data.items.head

    val expectedMergedWork = sierraPhysicalWork.withData { data =>
      data.copy(
        otherIdentifiers = sierraPhysicalWork.data.otherIdentifiers ++ miroWork.identifiers,
        thumbnail = miroWork.data.thumbnail,
        items = List(
          sierraItem.copy(
            locations = sierraItem.locations ++ miroItem.locations
          )
        ),
        images = miroWork.data.images,
        merged = true
      )
    }

    val expectedRedirectedWork =
      UnidentifiedRedirectedWork(
        sourceIdentifier = miroWork.sourceIdentifier,
        version = miroWork.version,
        redirect = IdentifiableRedirect(sierraPhysicalWork.sourceIdentifier))

    val expectedImage = miroWork.data.images.head mergeWith (
      sourceWork = Identifiable(sierraPhysicalWork.sourceIdentifier),
      fullText = createFulltext(List(sierraPhysicalWork, miroWork))
    )

    result.works should contain theSameElementsAs List(
      expectedMergedWork,
      expectedRedirectedWork)
    result.images should contain theSameElementsAs List(
      expectedImage
    )
  }

  it(
    "merges a Sierra Sierra picture/digital image/3D object digital work with a non-historical-library Miro work") {
    val result = merger.merge(
      works = Seq(sierraDigitalWork, miroWork)
    )

    result.works.size shouldBe 2

    val sierraItem = sierraDigitalWork.data.items.head
    val miroItem = miroWork.data.items.head

    val expectedMergedWork = sierraDigitalWork.withData { data =>
      data.copy(
        otherIdentifiers = sierraDigitalWork.data.otherIdentifiers ++ miroWork.identifiers,
        thumbnail = miroWork.data.thumbnail,
        items = List(
          sierraItem.copy(
            locations = sierraItem.locations ++ miroItem.locations
          )
        ),
        images = miroWork.data.images,
        merged = true
      )
    }

    val expectedRedirectedWork =
      UnidentifiedRedirectedWork(
        sourceIdentifier = miroWork.sourceIdentifier,
        version = miroWork.version,
        redirect = IdentifiableRedirect(sierraDigitalWork.sourceIdentifier))

    val expectedImage = miroWork.data.images.head mergeWith (
      sourceWork = Identifiable(sierraDigitalWork.sourceIdentifier),
      fullText = createFulltext(List(sierraDigitalWork, miroWork))
    )

    result.works should contain theSameElementsAs List(
      expectedMergedWork,
      expectedRedirectedWork)
    result.images should contain theSameElementsAs List(
      expectedImage
    )
  }

  it("merges a non-picture Sierra work with a METS work") {
    val result = merger.merge(
      works = Seq(sierraPhysicalWork, metsWork)
    )

    result.works.size shouldBe 2

    val physicalItem = sierraPhysicalWork.data.items.head
    val digitalItem = metsWork.data.items.head

    val expectedMergedWork = sierraPhysicalWork.withData { data =>
      data.copy(
        merged = true,
        items = List(
          physicalItem.copy(
            locations = physicalItem.locations ++ digitalItem.locations
          )
        ),
        thumbnail = metsWork.data.thumbnail,
      )
    }

    val expectedRedirectedWork =
      UnidentifiedRedirectedWork(
        sourceIdentifier = metsWork.sourceIdentifier,
        version = metsWork.version,
        redirect = IdentifiableRedirect(sierraPhysicalWork.sourceIdentifier)
      )

    result.works should contain theSameElementsAs List(
      expectedMergedWork,
      expectedRedirectedWork)
    result.images shouldBe empty
  }

  it("merges a picture Sierra work with a METS work") {
    val result = merger.merge(
      works = Seq(sierraPictureWork, metsWork)
    )

    result.works.size shouldBe 2

    val physicalItem = sierraPictureWork.data.items.head
    val digitalItem = metsWork.data.items.head

    val expectedMergedWork = sierraPictureWork.withData { data =>
      data.copy(
        merged = true,
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
      UnidentifiedRedirectedWork(
        sourceIdentifier = metsWork.sourceIdentifier,
        version = metsWork.version,
        redirect = IdentifiableRedirect(sierraPictureWork.sourceIdentifier)
      )

    val expectedImage = metsWork.data.images.head mergeWith (
      sourceWork = Identifiable(sierraPictureWork.sourceIdentifier),
      fullText = createFulltext(List(sierraPictureWork, metsWork))
    )

    result.works should contain theSameElementsAs List(
      expectedMergedWork,
      expectedRedirectedWork)
    result.images should contain theSameElementsAs List(
      expectedImage
    )
  }

  it(
    "merges a 3D object physical Sierra work with a digital Sierra work, a non-historical-library Miro work and a METS work") {
    val result = merger.merge(
      works = Seq(sierraPhysicalWork, sierraDigitised, miroWork, metsWork)
    )

    result.works.size shouldBe 4

    val sierraItem = sierraPhysicalWork.data.items.head
    val metsItem = metsWork.data.items.head

    val expectedMergedWork = sierraPhysicalWork.withData { data =>
      data.copy(
        otherIdentifiers = sierraPhysicalWork.data.otherIdentifiers ++ miroWork.identifiers ++ sierraDigitised.identifiers,
        thumbnail = metsWork.data.thumbnail,
        items = List(
          sierraItem.copy(
            locations = sierraItem.locations ++ metsItem.locations
          )
        ),
        images = miroWork.data.images,
        merged = true
      )
    }

    val expectedRedirectedDigitalWork =
      UnidentifiedRedirectedWork(
        sourceIdentifier = sierraDigitised.sourceIdentifier,
        version = sierraDigitised.version,
        redirect = IdentifiableRedirect(sierraPhysicalWork.sourceIdentifier)
      )

    val expectedMiroRedirectedWork =
      UnidentifiedRedirectedWork(
        sourceIdentifier = miroWork.sourceIdentifier,
        version = miroWork.version,
        redirect = IdentifiableRedirect(sierraPhysicalWork.sourceIdentifier))

    val expectedMetsRedirectedWork =
      UnidentifiedRedirectedWork(
        sourceIdentifier = metsWork.sourceIdentifier,
        version = metsWork.version,
        redirect = IdentifiableRedirect(sierraPhysicalWork.sourceIdentifier))

    val expectedImage = miroWork.data.images.head mergeWith (
      sourceWork = Identifiable(sierraPhysicalWork.sourceIdentifier),
      fullText = createFulltext(List(sierraPhysicalWork, miroWork))
    )

    result.works should contain theSameElementsAs List(
      expectedMergedWork,
      expectedRedirectedDigitalWork,
      expectedMiroRedirectedWork,
      expectedMetsRedirectedWork)
    result.images should contain theSameElementsAs List(
      expectedImage
    )
  }

  it("merges a multiple items physical Sierra work with a METS work") {
    val result = merger.merge(
      works = Seq(multipleItemsSierraWork, metsWork)
    )

    result.works.size shouldBe 2

    val sierraItems =
      multipleItemsSierraWork.data.items
    val metsItem = metsWork.data.items.head

    val expectedMergedWork = multipleItemsSierraWork.withData { data =>
      data.copy(
        thumbnail = metsWork.data.thumbnail,
        items = sierraItems :+ metsItem,
        merged = true
      )
    }

    val expectedMetsRedirectedWork =
      UnidentifiedRedirectedWork(
        sourceIdentifier = metsWork.sourceIdentifier,
        version = metsWork.version,
        redirect =
          IdentifiableRedirect(multipleItemsSierraWork.sourceIdentifier))

    result.works should contain theSameElementsAs List(
      expectedMergedWork,
      expectedMetsRedirectedWork)
    result.images shouldBe empty
  }

  it(
    "merges a multiple items physical Sierra work with a digital Sierra work and a METS work") {
    val result = merger.merge(
      works = Seq(multipleItemsSierraWork, sierraDigitised, metsWork)
    )

    result.works.size shouldBe 3

    val sierraItems = multipleItemsSierraWork.data.items
    val metsItem = metsWork.data.items.head

    val expectedMergedWork = multipleItemsSierraWork.withData { data =>
      data.copy(
        otherIdentifiers = multipleItemsSierraWork.data.otherIdentifiers ++ sierraDigitised.identifiers,
        thumbnail = metsWork.data.thumbnail,
        items = sierraItems :+ metsItem,
        merged = true
      )
    }

    val expectedRedirectedDigitalWork =
      UnidentifiedRedirectedWork(
        sourceIdentifier = sierraDigitised.sourceIdentifier,
        version = sierraDigitised.version,
        redirect =
          IdentifiableRedirect(multipleItemsSierraWork.sourceIdentifier)
      )

    val expectedMetsRedirectedWork =
      UnidentifiedRedirectedWork(
        sourceIdentifier = metsWork.sourceIdentifier,
        version = metsWork.version,
        redirect =
          IdentifiableRedirect(multipleItemsSierraWork.sourceIdentifier))

    result.works should contain theSameElementsAs List(
      expectedMergedWork,
      expectedRedirectedDigitalWork,
      expectedMetsRedirectedWork)

    result.images shouldBe empty
  }

  it(
    "suppresses a single historical library Miro target and creates no images for it") {
    val result = merger.merge(List(historicalLibraryMiroWork))

    result.works should have length 1
    result.works.head shouldBe an[UnidentifiedInvisibleWork]
    result.works.head.sourceIdentifier shouldBe historicalLibraryMiroWork.sourceIdentifier
    result.works.head
      .asInstanceOf[UnidentifiedInvisibleWork]
      .invisibilityReasons
      .head shouldBe InvisibilityReason.UnlinkedHistoricalLibraryMiro()
    result.images shouldBe empty
  }

  it("creates an image for a single non-historical-library Miro target") {
    val result = merger.merge(List(miroWork))

    result.works should have length 1
    result.works.head shouldBe miroWork
    result.images should have length 1
    result.images.head shouldBe miroWork.data.images.head.mergeWith(
      sourceWork = Identifiable(miroWork.sourceIdentifier),
      fullText = createFulltext(List(miroWork))
    )
  }
}
