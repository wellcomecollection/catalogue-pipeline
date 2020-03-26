package uk.ac.wellcome.platform.merger.services

import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.models.work.generators.WorksGenerators
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.merger.fixtures.ImageFulltextAccess

class PlatformMergerTest
    extends FunSpec
    with WorksGenerators
    with Matchers
    with ImageFulltextAccess {
  val digitalLocationCCBYNC = createDigitalLocationWith(
    license = Some(License.CCBYNC))
  val digitalLocationNoLicense = digitalLocationCCBYNC.copy(license = None)

  private val sierraPhysicalWork = createSierraPhysicalWork
  private val multipleItemsSierraWork = createSierraWorkWithTwoPhysicalItems
  private val sierraDigitalWork = createSierraDigitalWorkWith(
    items = List(createDigitalItemWith(List(digitalLocationNoLicense))))
  private val sierraPictureWork = createUnidentifiedSierraWorkWith(
    items = List(createPhysicalItem),
    workType = Some(WorkType.Pictures)
  )
  private val miroWork = createMiroWork
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

  private val merger = PlatformMerger

  it("merges a Sierra physical and Sierra digital work") {
    val result = merger.merge(
      works = Seq(sierraPhysicalWork, sierraDigitalWork)
    )

    result.works.size shouldBe 2

    val physicalItem = sierraPhysicalWork.data.items.head
    val digitalItem = sierraDigitalWork.data.items.head

    val expectedMergedWork = sierraPhysicalWork.withData { data =>
      data.copy(
        merged = true,
        otherIdentifiers = sierraPhysicalWork.data.otherIdentifiers ++ sierraDigitalWork.identifiers,
        items = List(
          physicalItem.copy(
            locations = physicalItem.locations ++ digitalItem.locations
          )
        )
      )
    }

    val expectedRedirectedWork =
      UnidentifiedRedirectedWork(
        sourceIdentifier = sierraDigitalWork.sourceIdentifier,
        version = sierraDigitalWork.version,
        redirect = IdentifiableRedirect(sierraPhysicalWork.sourceIdentifier)
      )

    result.works should contain theSameElementsAs List(
      expectedMergedWork,
      expectedRedirectedWork)
  }

  it("merges a Sierra physical work with a single-page Miro work") {
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
      parentWork = Identifiable(sierraPhysicalWork.sourceIdentifier),
      fullText = createFulltext(List(sierraPhysicalWork, miroWork))
    )

    result.works should contain theSameElementsAs List(
      expectedMergedWork,
      expectedRedirectedWork)
    result.images should contain theSameElementsAs List(
      expectedImage
    )
  }

  it("merges a Sierra digital work with a single-page Miro work") {
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
      parentWork = Identifiable(sierraDigitalWork.sourceIdentifier),
      fullText = createFulltext(List(sierraDigitalWork, miroWork))
    )

    result.works should contain theSameElementsAs List(
      expectedMergedWork,
      expectedRedirectedWork)
    result.images should contain theSameElementsAs List(
      expectedImage
    )
  }

  it(
    "merges a physical Sierra work and a digital Sierra work with a single-page Miro work") {
    val result = merger.merge(
      works = Seq(sierraPhysicalWork, sierraDigitalWork, miroWork)
    )

    result.works.size shouldBe 3

    val sierraItem = sierraPhysicalWork.data.items.head
    val digitalItem = sierraDigitalWork.data.items.head
    val miroItem = miroWork.data.items.head

    val expectedMergedWork = sierraPhysicalWork.withData { data =>
      data.copy(
        otherIdentifiers = sierraPhysicalWork.data.otherIdentifiers ++ sierraDigitalWork.identifiers ++ miroWork.identifiers,
        thumbnail = miroWork.data.thumbnail,
        items = List(
          sierraItem.copy(
            locations = sierraItem.locations ++ digitalItem.locations ++ miroItem.locations
          )
        ),
        images = miroWork.data.images,
        merged = true
      )
    }

    val expectedRedirectedDigitalWork =
      UnidentifiedRedirectedWork(
        sourceIdentifier = sierraDigitalWork.sourceIdentifier,
        version = sierraDigitalWork.version,
        redirect = IdentifiableRedirect(sierraPhysicalWork.sourceIdentifier)
      )

    val expectedMiroRedirectedWork =
      UnidentifiedRedirectedWork(
        sourceIdentifier = miroWork.sourceIdentifier,
        version = miroWork.version,
        redirect = IdentifiableRedirect(sierraPhysicalWork.sourceIdentifier))

    val expectedImage = miroWork.data.images.head mergeWith (
      parentWork = Identifiable(sierraPhysicalWork.sourceIdentifier),
      fullText = createFulltext(List(sierraPhysicalWork, miroWork))
    )

    result.works should contain theSameElementsAs List(
      expectedMergedWork,
      expectedRedirectedDigitalWork,
      expectedMiroRedirectedWork)
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
      parentWork = Identifiable(sierraPictureWork.sourceIdentifier),
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
    "merges a physical non-picture Sierra work with a digital Sierra work, a single-page Miro work and a METS work") {
    val result = merger.merge(
      works = Seq(sierraPhysicalWork, sierraDigitalWork, miroWork, metsWork)
    )

    result.works.size shouldBe 4

    val sierraItem = sierraPhysicalWork.data.items.head
    val metsItem = metsWork.data.items.head

    val expectedMergedWork = sierraPhysicalWork.withData { data =>
      data.copy(
        otherIdentifiers = sierraPhysicalWork.data.otherIdentifiers ++ sierraDigitalWork.identifiers ++ miroWork.identifiers,
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
        sourceIdentifier = sierraDigitalWork.sourceIdentifier,
        version = sierraDigitalWork.version,
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
      parentWork = Identifiable(sierraPhysicalWork.sourceIdentifier),
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

  it("merges a multiple items physical Sierra work with a digital work") {
    val result = merger.merge(
      works = Seq(multipleItemsSierraWork, sierraDigitalWork)
    )

    result.works.size shouldBe 2

    val sierraItems =
      multipleItemsSierraWork.data.items
    val digitalItem = sierraDigitalWork.data.items.head

    val expectedMergedWork = multipleItemsSierraWork.withData { data =>
      data.copy(
        otherIdentifiers = multipleItemsSierraWork.data.otherIdentifiers ++ sierraDigitalWork.identifiers,
        items = sierraItems :+ digitalItem,
        merged = true
      )
    }

    val expectedRedirectedDigitalWork =
      UnidentifiedRedirectedWork(
        sourceIdentifier = sierraDigitalWork.sourceIdentifier,
        version = sierraDigitalWork.version,
        redirect =
          IdentifiableRedirect(multipleItemsSierraWork.sourceIdentifier)
      )

    result.works should contain theSameElementsAs List(
      expectedMergedWork,
      expectedRedirectedDigitalWork)
    result.images shouldBe empty
  }

  it(
    "merges a multiple items physical Sierra work with a digital Sierra work and a METS work") {
    val result = merger.merge(
      works = Seq(multipleItemsSierraWork, sierraDigitalWork, metsWork)
    )

    result.works.size shouldBe 3

    val sierraItems = multipleItemsSierraWork.data.items
    val metsItem = metsWork.data.items.head

    val expectedMergedWork = multipleItemsSierraWork.withData { data =>
      data.copy(
        otherIdentifiers = multipleItemsSierraWork.data.otherIdentifiers ++ sierraDigitalWork.identifiers,
        thumbnail = metsWork.data.thumbnail,
        items = sierraItems :+ metsItem,
        merged = true
      )
    }

    val expectedRedirectedDigitalWork =
      UnidentifiedRedirectedWork(
        sourceIdentifier = sierraDigitalWork.sourceIdentifier,
        version = sierraDigitalWork.version,
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

  it("merges fields from Calm work if present") {
    val calmLocation = PhysicalLocation(
      locationType = LocationType("scmac"),
      label = "Closed stores Arch. & MSS",
      accessConditions = Nil
    )
    val calmWork = createUnidentifiedCalmWork(
      data = WorkData(
        title = Some("123"),
        collection = Some(Collection("ref/no", CollectionLevel.Item)),
        physicalDescription = Some("description"),
        contributors = List(Contributor(Agent("agent"), Nil)),
        subjects = List(Subject("subject", Nil)),
        language = Some(Language("en.gb", "English")),
        notes = List(FindingAids("here")),
        workType = Some(WorkType.ArchiveItem),
        items = List(Item(None, List(calmLocation))),
        edition = Some("Should not be merged")
      )
    )

    val works = merger.merge(Seq(sierraPhysicalWork, calmWork)).works

    works.size shouldBe 2
    val work = works(1).asInstanceOf[UnidentifiedWork]
    work.sourceIdentifier shouldBe sierraPhysicalWork.sourceIdentifier
    work.data.title shouldBe calmWork.data.title
    work.data.collection shouldBe calmWork.data.collection
    work.data.physicalDescription shouldBe calmWork.data.physicalDescription
    work.data.contributors shouldBe calmWork.data.contributors
    work.data.subjects shouldBe calmWork.data.subjects
    work.data.language shouldBe calmWork.data.language
    work.data.notes shouldBe calmWork.data.notes
    work.data.workType shouldBe calmWork.data.workType
    work.data.edition shouldBe None
  }
}
