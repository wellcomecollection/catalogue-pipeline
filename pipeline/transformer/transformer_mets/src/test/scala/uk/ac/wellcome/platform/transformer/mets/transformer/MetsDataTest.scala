package uk.ac.wellcome.platform.transformer.mets.transformer

import java.time.Instant

import org.scalatest.Inside
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.models.work.internal._
import WorkState.Source
import uk.ac.wellcome.fixtures.RandomGenerators

class MetsDataTest
    extends AnyFunSpec
    with RandomGenerators
    with Matchers
    with Inside {

  it("creates a invisible work with an item and a license") {
    val bNumber = randomAlphanumeric(10)
    val metsData =
      MetsData(recordIdentifier = bNumber, accessConditionDz = Some("CC-BY-NC"))
    val version = 1
    val expectedSourceIdentifier = SourceIdentifier(
      IdentifierType("mets", "METS"),
      ontologyType = "Work",
      value = bNumber)

    val url = s"https://wellcomelibrary.org/iiif/$bNumber/manifest"
    val digitalLocation = DigitalLocationDeprecated(
      url,
      LocationType("iiif-presentation"),
      license = Some(License.CCBYNC))

    val createdDate = Instant.now()

    val unidentifiableItem =
      Item(id = IdState.Unidentifiable, locations = List(digitalLocation))
    metsData.toWork(version,createdDate).right.get shouldBe Work.Invisible[Source](
      version = version,
      state = Source(expectedSourceIdentifier,createdDate),
      data = WorkData[DataState.Unidentified](
        items = List(unidentifiableItem),
        mergeCandidates = List(
          MergeCandidate(
            identifier = SourceIdentifier(
              identifierType = IdentifierType("sierra-system-number"),
              ontologyType = "Work",
              value = bNumber
            ),
            reason = "METS work"
          )
        )
      )
    )
  }

  it("creates a invisible work with an item and no license") {
    val bNumber = randomAlphanumeric(10)
    val metsData =
      MetsData(recordIdentifier = bNumber, accessConditionDz = None)
    val version = 1
    val expectedSourceIdentifier = SourceIdentifier(
      IdentifierType("mets", "METS"),
      ontologyType = "Work",
      value = bNumber)

    val url = s"https://wellcomelibrary.org/iiif/$bNumber/manifest"
    val digitalLocation =
      DigitalLocationDeprecated(
        url,
        LocationType("iiif-presentation"),
        license = None)

    val createdDate = Instant.now()

    val unidentifiableItem =
      Item(id = IdState.Unidentifiable, locations = List(digitalLocation))
    metsData.toWork(version, createdDate).right.get shouldBe Work.Invisible[Source](
      version = version,
      state = Source(expectedSourceIdentifier, createdDate),
      data = WorkData[DataState.Unidentified](
        items = List(unidentifiableItem),
        mergeCandidates = List(
          MergeCandidate(
            identifier = SourceIdentifier(
              identifierType = IdentifierType("sierra-system-number"),
              ontologyType = "Work",
              value = bNumber
            ),
            reason = "METS work"
          )
        )
      )
    )
  }

  it("fails creating a work if it cannot parse the license") {
    val bNumber = randomAlphanumeric(10)
    val metsData =
      MetsData(recordIdentifier = bNumber, accessConditionDz = Some("blah"))
    val version = 1

    metsData.toWork(version, Instant.now()).left.get shouldBe a[Exception]

  }

  it("can create a license if it matches the license label lowercase") {
    val metsData =
      MetsData(
        recordIdentifier = randomAlphanumeric(10),
        accessConditionDz = Some("in copyright"))

    inside(metsData.toWork(1, Instant.now()).right.get.data.items) {
      case List(
          Item(
            IdState.Unidentifiable,
            _,
            List(DigitalLocationDeprecated(_, _, license, _, _)))) =>
        license shouldBe Some(License.InCopyright)
    }
  }

  it("can create a license if it matches the license label") {
    val metsData =
      MetsData(
        recordIdentifier = randomAlphanumeric(10),
        accessConditionDz = Some("In copyright"))

    inside(metsData.toWork(1, Instant.now()).right.get.data.items) {
      case List(
          Item(
            IdState.Unidentifiable,
            _,
            List(DigitalLocationDeprecated(_, _, license, _, _)))) =>
        license shouldBe Some(License.InCopyright)
    }
  }

  it("can create a license if it matches the license url") {
    val metsData =
      MetsData(
        recordIdentifier = randomAlphanumeric(10),
        accessConditionDz = Some(License.InCopyright.url))
    inside(metsData.toWork(1, Instant.now()).right.get.data.items) {
      case List(
          Item(
            IdState.Unidentifiable,
            _,
            List(DigitalLocationDeprecated(_, _, license, _, _)))) =>
        license shouldBe Some(License.InCopyright)
    }
  }

  it("maps Copyright not cleared to In copyright") {
    val metsData = MetsData(
      recordIdentifier = randomAlphanumeric(10),
      accessConditionDz = Some("Copyright not cleared"))
    val result = metsData.toWork(1, Instant.now())

    inside(result.right.get.data.items) {
      case List(
          Item(
            IdState.Unidentifiable,
            _,
            List(DigitalLocationDeprecated(_, _, license, _, _)))) =>
        license shouldBe Some(License.InCopyright)
    }
  }

  it("can create a license for rightsstatements.org/page/InC/1.0/?language=en") {
    val metsData =
      MetsData(
        recordIdentifier = randomAlphanumeric(10),
        accessConditionDz =
          Some("rightsstatements.org/page/InC/1.0/?language=en"))
    val result = metsData.toWork(1, Instant.now())

    inside(result.right.get.data.items) {
      case List(
          Item(
            IdState.Unidentifiable,
            _,
            List(DigitalLocationDeprecated(_, _, license, _, _)))) =>
        license shouldBe Some(License.InCopyright)
    }
  }

  it("maps All Rights Reserved to In Copyright license") {
    val metsData =
      MetsData(
        recordIdentifier = randomAlphanumeric(10),
        accessConditionDz = Some("All Rights Reserved"))
    val result = metsData.toWork(1, Instant.now())

    inside(result.right.get.data.items) {
      case List(
          Item(
            IdState.Unidentifiable,
            _,
            List(DigitalLocationDeprecated(_, _, license, _, _)))) =>
        license shouldBe Some(License.InCopyright)
    }
  }

  it("creates a invisible work with a thumbnail location") {
    val metsData = MetsData(
      recordIdentifier = randomAlphanumeric(10),
      accessConditionDz = Some("CC-BY-NC"),
      fileReferencesMapping = List(
        "id" -> FileReference("l", "location.jp2", Some("image/jp2"))
      )
    )
    val result = metsData.toWork(1, Instant.now())
    result shouldBe a[Right[_, _]]
    result.right.get.data.thumbnail shouldBe Some(
      DigitalLocationDeprecated(
        s"https://dlcs.io/thumbs/wellcome/5/location.jp2/full/!200,200/0/default.jpg",
        LocationType("thumbnail-image"),
        license = Some(License.CCBYNC)
      )
    )
  }

  it("creates a invisible work with a title page thumbnail") {
    val metsData = MetsData(
      recordIdentifier = randomAlphanumeric(10),
      accessConditionDz = Some("CC-BY-NC"),
      fileReferencesMapping = List(
        "id" -> FileReference("l", "location.jp2", Some("image/jp2")),
        "title-id" -> FileReference("l", "title.jp2", Some("image/jp2"))
      ),
      titlePageId = Some("title-id")
    )
    val result = metsData.toWork(1, Instant.now())
    result shouldBe a[Right[_, _]]
    result.right.get.data.thumbnail shouldBe Some(
      DigitalLocationDeprecated(
        s"https://dlcs.io/thumbs/wellcome/5/title.jp2/full/!200,200/0/default.jpg",
        LocationType("thumbnail-image"),
        license = Some(License.CCBYNC)
      )
    )
  }

  it("creates a invisible work without a thumbnail for restricted works") {
    val metsData = MetsData(
      recordIdentifier = randomAlphanumeric(10),
      accessConditionDz = Some("CC-BY-NC"),
      accessConditionStatus = Some("restricted"),
      fileReferencesMapping = List(
        "id" -> FileReference("l", "location.jp2", Some("image/jp2"))
      )
    )
    val result = metsData.toWork(1, Instant.now())
    result shouldBe a[Right[_, _]]
    result.right.get.data.thumbnail shouldBe None
  }

  it("serves the thumbnail from wellcomelibrary for PDFs") {
    val bnumber = randomAlphanumeric(10)
    val assetId = "location.pdf"
    val metsData = MetsData(
      recordIdentifier = bnumber,
      accessConditionDz = Some("CC-BY-NC"),
      fileReferencesMapping = List(
        "id" -> FileReference("l", "location.pdf")
      )
    )
    val result = metsData.toWork(1, Instant.now())
    result shouldBe a[Right[_, _]]
    result.right.get.data.thumbnail shouldBe Some(
      DigitalLocationDeprecated(
        s"https://wellcomelibrary.org/pdfthumbs/${bnumber}/0/${assetId}.jpg",
        LocationType("thumbnail-image"),
        license = Some(License.CCBYNC)
      )
    )
  }

  it("does not add a thumbnail if the file is a video") {
    val metsData = MetsData(
      recordIdentifier = randomAlphanumeric(10),
      accessConditionDz = Some("CC-BY-NC"),
      fileReferencesMapping = List(
        "id" -> FileReference("v", "video.mpg", Some("video/mpeg"))
      )
    )
    val result = metsData.toWork(1, Instant.now())
    result shouldBe a[Right[_, _]]
    result.right.get.data.thumbnail shouldBe None
  }

  it("does not add a thumbnail if the file is an audio") {
    val metsData = MetsData(
      recordIdentifier = randomAlphanumeric(10),
      accessConditionDz = Some("CC-BY-NC"),
      fileReferencesMapping = List(
        "id" -> FileReference("v", "video.mp3", Some("audio/x-mpeg-3"))
      )
    )
    val result = metsData.toWork(1, Instant.now())
    result shouldBe a[Right[_, _]]
    result.right.get.data.thumbnail shouldBe None
  }

  it("uses the IIIF info.json for image URLs") {
    val metsData = MetsData(
      recordIdentifier = randomAlphanumeric(10),
      accessConditionDz = Some("CC-BY-NC"),
      fileReferencesMapping = List(
        "id" -> FileReference("l", "location.jp2", Some("image/jp2"))
      )
    )
    val result = metsData.toWork(1, Instant.now())
    result shouldBe a[Right[_, _]]
    result.right.get.data.images.head.location shouldBe DigitalLocationDeprecated(
      url = s"https://dlcs.io/iiif-img/wellcome/5/location.jp2/info.json",
      locationType = LocationType("iiif-image"),
      license = Some(License.CCBYNC)
    )
  }

  it("creates a work with a single accessCondition") {
    val result = MetsData(
      recordIdentifier = "ID",
      accessConditionStatus = Some("Requires registration"),
    ).toWork(1, Instant.now())
    result shouldBe a[Right[_, _]]
    inside(result.right.get.data.items.head.locations.head) {
      case DigitalLocationDeprecated(_, _, _, _, accessConditions) =>
        accessConditions shouldBe List(
          AccessCondition(
            status = Some(AccessStatus.OpenWithAdvisory)
          )
        )

    }
  }

  it("creates a work with all images") {
    val result = MetsData(
      recordIdentifier = "ID",
      accessConditionDz = Some("CC-BY-NC"),
      fileReferencesMapping = List(
        "A" -> FileReference("A", "location1.jp2", Some("image/jp2")),
        "B" -> FileReference("B", "location2.jp2", Some("image/jp2")),
        "C" -> FileReference("C", "location3.jp2", Some("image/jp2")),
        "D" -> FileReference("D", "location4.jp2", Some("application/pdf")),
        "E" -> FileReference("E", "location4.jp2", Some("video/mpeg"))
      )
    ).toWork(1, Instant.now())
    result shouldBe a[Right[_, _]]
    val images = result.right.get.data.images
    images should have length 3
    images.map(_.id.allSourceIdentifiers.head.value) should contain theSameElementsAs List(
      "ID/A",
      "ID/B",
      "ID/C")
  }

  it("creates a work without images for restricted access statuses") {
    val result = MetsData(
      recordIdentifier = "ID",
      accessConditionDz = Some("CC-BY-NC"),
      accessConditionStatus = Some("Restricted files"),
      fileReferencesMapping = List(
        "A" -> FileReference("A", "location1.jp2", Some("image/jp2")),
        "B" -> FileReference("B", "location2.jp2", Some("image/jp2"))
      )
    ).toWork(1, Instant.now())
    result shouldBe a[Right[_, _]]
    result.right.get.data.images shouldBe empty
  }

  it("creates a work with a single accessCondition including usage terms") {
    val result = MetsData(
      recordIdentifier = "ID",
      accessConditionStatus = Some("Clinical Images"),
      accessConditionUsage = Some("Please ask nicely")
    ).toWork(1, Instant.now())
    result shouldBe a[Right[_, _]]
    inside(result.right.get.data.items.head.locations.head) {
      case DigitalLocationDeprecated(_, _, _, _, accessConditions) =>
        accessConditions shouldBe List(
          AccessCondition(
            status = Some(AccessStatus.Restricted),
            terms = Some("Please ask nicely")
          ))
    }
  }

  it("does not add access condition if all fields are empty") {
    val result = MetsData(
      recordIdentifier = "ID",
      accessConditionStatus = None,
      accessConditionUsage = None
    ).toWork(1, Instant.now())
    result shouldBe a[Right[_, _]]
    inside(result.right.get.data.items.head.locations.head) {
      case DigitalLocationDeprecated(_, _, _, _, accessConditions) =>
        accessConditions shouldBe List()
    }
  }

  it("maps restricted files to Restricted AccessCondition") {
    val result = MetsData(
      recordIdentifier = "ID",
      accessConditionStatus = Some("Restricted files")
    ).toWork(1, Instant.now())
    result shouldBe a[Right[_, _]]
    inside(result.right.get.data.items.head.locations.head) {
      case DigitalLocationDeprecated(_, _, _, _, accessConditions) =>
        accessConditions shouldBe
          List(
            AccessCondition(
              status = Some(AccessStatus.Restricted)
            )
          )
    }
  }

  it("fails creating a work when unknown AccessStatus") {
    val result = MetsData(
      recordIdentifier = "ID",
      accessConditionStatus = Some("Kanye West"),
    ).toWork(1, Instant.now())
    result shouldBe a[Left[_, _]]
  }
}
