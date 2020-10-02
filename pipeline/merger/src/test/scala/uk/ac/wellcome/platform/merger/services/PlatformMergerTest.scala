package uk.ac.wellcome.platform.merger.services

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks._
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.merger.generators.WorksWithImagesGenerators
import WorkState.{Merged, Source}
import WorkFsm._
import SourceWork._

class PlatformMergerTest
    extends AnyFunSpec
    with WorksWithImagesGenerators
    with Matchers {
  val digitalLocationCCBYNC = createDigitalLocationWith(
    license = Some(License.CCBYNC))
  val digitalLocationNoLicense = digitalLocationCCBYNC.copy(license = None)

  val sierraDigitised = createSierraSourceWork
  val sierraPhysicalWork = createSierraPhysicalWork.copy(
    data = createSierraPhysicalWork.data.copy(
      mergeCandidates = List(
        MergeCandidate(
          sierraDigitised.sourceIdentifier,
          Some("Physical/digitised Sierra work"))),
      format = Some(Format.`3DObjects`)
    ))
  val zeroItemSierraWork = createSierraSourceWorkWith(
    items = Nil,
    format = Some(Format.Pictures)
  )
  private val multipleItemsSierraWork =
    createSierraWorkWithTwoPhysicalItems.copy(
      data = createSierraWorkWithTwoPhysicalItems.data.copy(
        mergeCandidates = List(
          MergeCandidate(
            sierraDigitised.sourceIdentifier,
            Some("Physical/digitised Sierra work")))
      ))
  private val sierraDigitalWork = createSierraSourceWorkWith(
    items = List(createDigitalItemWith(List(digitalLocationNoLicense))),
    format = Some(Format.DigitalImages)
  )
  private val sierraPictureWork = createSierraSourceWorkWith(
    items = List(createPhysicalItem),
    format = Some(Format.Pictures)
  )
  private val miroWork = createMiroWorkWith(
    sourceIdentifier = createNonHistoricalLibraryMiroSourceIdentifier,
    images = List(createUnmergedMiroImage)
  )
  private val metsWork =
    createInvisibleMetsSourceWorkWith(
      items = List(createDigitalItemWith(List(digitalLocationCCBYNC))),
      images = List(createUnmergedMetsImage)
    ).mapData { data =>
      data.copy(
        thumbnail = Some(
          DigitalLocationDeprecated(
            url = "https://path.to/thumbnail.jpg",
            locationType = LocationType("thumbnail-image"),
            license = Some(License.CCBY)
          )
        )
      )
    }
  val calmWork = createCalmSourceWork

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
    "merges a Sierra picture/digital image/3D object physical work with a non-historical-library Miro work") {
    val result = merger.merge(
      works = Seq(sierraPhysicalWork, miroWork)
    )

    result.works.size shouldBe 2

    val sierraItem = sierraPhysicalWork.data.items.head
    val miroItem = miroWork.data.items.head

    val expectedMergedWork = sierraPhysicalWork
      .transition[Merged](1)
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
        state = Merged(miroWork.sourceIdentifier, 0),
        version = miroWork.version,
        redirect = IdState.Identifiable(sierraPhysicalWork.sourceIdentifier))

    val expectedImage = miroWork.data.images.head mergeWith (
      canonicalWork = expectedMergedWork.toSourceWork,
      redirectedWork = Some(miroWork.toSourceWork),
      nMergedSources = 1
    )

    result.works should contain theSameElementsAs List(
      expectedMergedWork,
      expectedRedirectedWork)
    result.images should contain theSameElementsAs List(
      expectedImage
    )
  }

  it("merges a zero-item Sierra work with a Miro work") {
    val result = merger.merge(
      works = Seq(zeroItemSierraWork, miroWork)
    )

    result.works.size shouldBe 2

    val expectedMergedWork = zeroItemSierraWork
      .transition[Merged](1)
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
        state = Merged(miroWork.sourceIdentifier, 0),
        version = miroWork.version,
        redirect = IdState.Identifiable(zeroItemSierraWork.sourceIdentifier)
      )

    val expectedImage = miroWork.data.images.head mergeWith (
      canonicalWork = expectedMergedWork.toSourceWork,
      redirectedWork = Some(miroWork.toSourceWork),
      nMergedSources = 1
    )

    result.works should contain theSameElementsAs List(
      expectedMergedWork,
      expectedRedirectedWork
    )
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

    val expectedMergedWork = sierraDigitalWork
      .transition[Merged](1)
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
        state = Merged(miroWork.sourceIdentifier, 0),
        version = miroWork.version,
        redirect = IdState.Identifiable(sierraDigitalWork.sourceIdentifier))

    val expectedImage = miroWork.data.images.head mergeWith (
      canonicalWork = expectedMergedWork.toSourceWork,
      redirectedWork = Some(miroWork.toSourceWork),
      nMergedSources = 1
    )

    result.works should contain theSameElementsAs List(
      expectedMergedWork,
      expectedRedirectedWork)
    result.images should contain theSameElementsAs List(
      expectedImage
    )
  }

  it("does not merge a sierra work with multiple items with a linked Miro work") {
    val result = merger.merge(
      works = Seq(multipleItemsSierraWork, miroWork)
    )

    result.works.size shouldBe 2

    val expectedMergedWork = multipleItemsSierraWork
      .transition[Merged](1)
      .mapData { data =>
        data.copy(
          images = miroWork.data.images,
        )
      }

    result.works should contain theSameElementsAs Seq(
      miroWork.transition[Merged](0),
      expectedMergedWork)
  }

  it("merges a non-picture Sierra work with a METS work") {
    val result = merger.merge(
      works = Seq(sierraPhysicalWork, metsWork)
    )

    result.works.size shouldBe 2

    val physicalItem = sierraPhysicalWork.data.items.head
    val digitalItem = metsWork.data.items.head

    val expectedMergedWork = sierraPhysicalWork
      .transition[Merged](1)
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
        state = Merged(metsWork.sourceIdentifier, 0),
        version = metsWork.version,
        redirect = IdState.Identifiable(sierraPhysicalWork.sourceIdentifier)
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

    val expectedMergedWork = sierraPictureWork
      .transition[Merged](1)
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
        state = Merged(metsWork.sourceIdentifier, 0),
        version = metsWork.version,
        redirect = IdState.Identifiable(sierraPictureWork.sourceIdentifier)
      )

    val expectedImage = metsWork.data.images.head mergeWith (
      canonicalWork = expectedMergedWork.toSourceWork,
      redirectedWork = Some(metsWork.toSourceWork),
      nMergedSources = 1
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

    val expectedMergedWork = sierraPhysicalWork
      .transition[Merged](3)
      .mapData { data =>
        data.copy(
          otherIdentifiers = sierraPhysicalWork.data.otherIdentifiers
            ++ sierraDigitised.identifiers
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
        state = Merged(sierraDigitised.sourceIdentifier, 0),
        version = sierraDigitised.version,
        redirect = IdState.Identifiable(sierraPhysicalWork.sourceIdentifier)
      )

    val expectedMiroRedirectedWork =
      Work.Redirected[Merged](
        state = Merged(miroWork.sourceIdentifier, 0),
        version = miroWork.version,
        redirect = IdState.Identifiable(sierraPhysicalWork.sourceIdentifier))

    val expectedMetsRedirectedWork =
      Work.Redirected[Merged](
        state = Merged(metsWork.sourceIdentifier, 0),
        version = metsWork.version,
        redirect = IdState.Identifiable(sierraPhysicalWork.sourceIdentifier))

    val expectedImage = miroWork.data.images.head mergeWith (
      canonicalWork = expectedMergedWork.toSourceWork,
      redirectedWork = Some(miroWork.toSourceWork),
      nMergedSources = 3
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

    val expectedMergedWork = multipleItemsSierraWork
      .transition[Merged](1)
      .mapData { data =>
        data.copy(
          thumbnail = metsWork.data.thumbnail,
          items = sierraItems :+ metsItem,
        )
      }

    val expectedMetsRedirectedWork =
      Work.Redirected[Merged](
        state = Merged(metsWork.sourceIdentifier, 0),
        version = metsWork.version,
        redirect =
          IdState.Identifiable(multipleItemsSierraWork.sourceIdentifier))

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

    val expectedMergedWork = multipleItemsSierraWork
      .transition[Merged](2)
      .mapData { data =>
        data.copy(
          otherIdentifiers = multipleItemsSierraWork.data.otherIdentifiers ++ sierraDigitised.identifiers,
          thumbnail = metsWork.data.thumbnail,
          items = sierraItems :+ metsItem,
        )
      }

    val expectedRedirectedDigitalWork =
      Work.Redirected[Merged](
        state = Merged(sierraDigitised.sourceIdentifier, 0),
        version = sierraDigitised.version,
        redirect =
          IdState.Identifiable(multipleItemsSierraWork.sourceIdentifier)
      )

    val expectedMetsRedirectedWork =
      Work.Redirected[Merged](
        state = Merged(metsWork.sourceIdentifier, 0),
        version = metsWork.version,
        redirect =
          IdState.Identifiable(multipleItemsSierraWork.sourceIdentifier))

    result.works should contain theSameElementsAs List(
      expectedMergedWork,
      expectedRedirectedDigitalWork,
      expectedMetsRedirectedWork)

    result.images shouldBe empty
  }

  it("creates an image for a single non-historical-library Miro target") {
    val result = merger.merge(List(miroWork))

    result.works should have length 1
    result.works.head shouldBe miroWork.transition[Merged](0)
    result.images should have length 1
    result.images.head shouldBe miroWork.data.images.head.mergeWith(
      canonicalWork = miroWork.toSourceWork,
      redirectedWork = None,
      nMergedSources = 0
    )
  }
}
