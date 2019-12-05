package uk.ac.wellcome.platform.merger.services

import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.models.work.generators.WorksGenerators
import uk.ac.wellcome.models.work.internal._

class MergerTest extends FunSpec with WorksGenerators with Matchers {
  val digitalLocationCCBYNC = createDigitalLocationWith(
    license = Some(License_CCBYNC))
  val digitalLocationNoLicense = digitalLocationCCBYNC.copy(license = None)

  private val sierraPhysicalWork = createSierraPhysicalWork
  private val sierraDigitalWork = createSierraDigitalWorkWith(
    items = List(createDigitalItemWith(List(digitalLocationNoLicense))))
  private val miroWork = createMiroWork
  private val metsWork =
    createUnidentifiedInvisibleMetsWorkWith(
      items = List(createDigitalItemWith(List(digitalLocationCCBYNC)))
    )
    .withData { data =>
      data.copy(
        thumbnail = Some(
          DigitalLocation(
            url = "https://path.to/thumbnail.jpg",
            locationType = LocationType("thumbnail-image"),
            license = Some(License_CCBY)
          )
        )
      )
    }

  private val merger = PlatformMerger

  it("merges a Sierra physical and Sierra digital work") {
    val result = merger.merge(
      works = Seq(sierraPhysicalWork, sierraDigitalWork)
    )

    result.size shouldBe 2

    val physicalItem =
      sierraPhysicalWork.data.items.head.asInstanceOf[Identifiable[Item]]
    val digitalItem = sierraDigitalWork.data.items.head

    val expectedMergedWork = sierraPhysicalWork.withData { data =>
      data.copy(
        merged = true,
        otherIdentifiers = sierraPhysicalWork.data.otherIdentifiers ++ sierraDigitalWork.identifiers,
        items = List(
          physicalItem.copy(
            agent = physicalItem.agent.copy(
              locations = physicalItem.agent.locations ++ digitalItem.agent.locations
            )
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

    result should contain theSameElementsAs List(
      expectedMergedWork,
      expectedRedirectedWork)
  }

  it("merges a Sierra physical work with a single-page Miro work") {
    val result = merger.merge(
      works = Seq(sierraPhysicalWork, miroWork)
    )

    result.size shouldBe 2

    val sierraItem =
      sierraPhysicalWork.data.items.head.asInstanceOf[Identifiable[Item]]
    val miroItem = miroWork.data.items.head

    val expectedMergedWork = sierraPhysicalWork.withData { data =>
      data.copy(
        otherIdentifiers = sierraPhysicalWork.data.otherIdentifiers ++ miroWork.identifiers,
        thumbnail = miroWork.data.thumbnail,
        items = List(
          sierraItem.copy(
            agent = sierraItem.agent.copy(
              locations = sierraItem.agent.locations ++ miroItem.agent.locations
            )
          )
        ),
        merged = true
      )
    }

    val expectedRedirectedWork =
      UnidentifiedRedirectedWork(
        sourceIdentifier = miroWork.sourceIdentifier,
        version = miroWork.version,
        redirect = IdentifiableRedirect(sierraPhysicalWork.sourceIdentifier))

    result should contain theSameElementsAs List(
      expectedMergedWork,
      expectedRedirectedWork)
  }

  it("merges a Sierra digital work with a single-page Miro work") {
    val result = merger.merge(
      works = Seq(sierraDigitalWork, miroWork)
    )

    result.size shouldBe 2

    val sierraItem =
      sierraDigitalWork.data.items.head.asInstanceOf[Unidentifiable[Item]]
    val miroItem = miroWork.data.items.head

    val expectedMergedWork = sierraDigitalWork.withData { data =>
      data.copy(
        otherIdentifiers = sierraDigitalWork.data.otherIdentifiers ++ miroWork.identifiers,
        thumbnail = miroWork.data.thumbnail,
        items = List(
          sierraItem.copy(
            agent = sierraItem.agent.copy(
              locations = sierraItem.agent.locations ++ miroItem.agent.locations
            )
          )
        ),
        merged = true
      )
    }

    val expectedRedirectedWork =
      UnidentifiedRedirectedWork(
        sourceIdentifier = miroWork.sourceIdentifier,
        version = miroWork.version,
        redirect = IdentifiableRedirect(sierraDigitalWork.sourceIdentifier))

    result should contain theSameElementsAs List(
      expectedMergedWork,
      expectedRedirectedWork)
  }

  it(
    "merges a physical Sierra work and a digital Sierra work with a single-page Miro work") {
    val result = merger.merge(
      works = Seq(sierraPhysicalWork, sierraDigitalWork, miroWork)
    )

    result.size shouldBe 3

    val sierraItem =
      sierraPhysicalWork.data.items.head.asInstanceOf[Identifiable[Item]]
    val digitalItem = sierraDigitalWork.data.items.head
    val miroItem = miroWork.data.items.head

    val expectedMergedWork = sierraPhysicalWork.withData { data =>
      data.copy(
        otherIdentifiers = sierraPhysicalWork.data.otherIdentifiers ++ sierraDigitalWork.identifiers ++ miroWork.identifiers,
        thumbnail = miroWork.data.thumbnail,
        items = List(
          sierraItem.copy(
            agent = sierraItem.agent.copy(
              locations = sierraItem.agent.locations ++ digitalItem.agent.locations ++ miroItem.agent.locations
            )
          )
        ),
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

    result should contain theSameElementsAs List(
      expectedMergedWork,
      expectedRedirectedDigitalWork,
      expectedMiroRedirectedWork)
  }

  it("merges a Sierra work wit a METS work") {
    val result = merger.merge(
      works = Seq(sierraPhysicalWork, metsWork)
    )

    result.size shouldBe 2

    val physicalItem =
      sierraPhysicalWork.data.items.head.asInstanceOf[Identifiable[Item]]
    val digitalItem = metsWork.data.items.head

    val expectedMergedWork = sierraPhysicalWork.withData { data =>
      data.copy(
        merged = true,
        items = List(
          physicalItem.copy(
            agent = physicalItem.agent.copy(
              locations = physicalItem.agent.locations ++ digitalItem.agent.locations
            )
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

    result should contain theSameElementsAs List(
      expectedMergedWork,
      expectedRedirectedWork)
  }

  it(
    "merges a physical Sierra work with a digital Sierra work, a single-page Miro work and a METS work") {
    val result = merger.merge(
      works = Seq(sierraPhysicalWork, sierraDigitalWork, miroWork, metsWork)
    )

    result.size shouldBe 4

    val sierraItem =
      sierraPhysicalWork.data.items.head.asInstanceOf[Identifiable[Item]]
    val metsItem = metsWork.data.items.head

    val expectedMergedWork = sierraPhysicalWork.withData { data =>
      data.copy(
        otherIdentifiers = sierraPhysicalWork.data.otherIdentifiers ++ sierraDigitalWork.identifiers ++ miroWork.identifiers,
        thumbnail = metsWork.data.thumbnail,
        items = List(
          sierraItem.copy(
            agent = sierraItem.agent.copy(
              locations = sierraItem.agent.locations ++ metsItem.agent.locations
            )
          )
        ),
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

    result should contain theSameElementsAs List(
      expectedMergedWork,
      expectedRedirectedDigitalWork,
      expectedMiroRedirectedWork,
      expectedMetsRedirectedWork)
  }

}
