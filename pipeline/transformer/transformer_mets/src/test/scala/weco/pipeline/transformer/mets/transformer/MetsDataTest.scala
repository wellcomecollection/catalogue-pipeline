package weco.pipeline.transformer.mets.transformer

import java.time.Instant
import org.scalatest.{EitherValues, Inside}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.work.WorkState.Source
import weco.catalogue.internal_model.identifiers._
import weco.catalogue.internal_model.locations._
import weco.catalogue.internal_model.work.DeletedReason.DeletedFromSource
import weco.catalogue.internal_model.work.InvisibilityReason.MetsWorksAreNotVisible
import weco.catalogue.internal_model.work._
import weco.pipeline.transformer.mets.fixtures.MetsGenerators

class MetsDataTest
    extends AnyFunSpec
    with MetsGenerators
    with Matchers
    with EitherValues
    with Inside {

  it("creates a invisible work with an item and a license") {
    val bibNumber = createBibNumber
    val metsData =
      MetsData(
        recordIdentifier = bibNumber,
        accessConditionDz = Some("CC-BY-NC")
      )
    val version = 1
    val expectedSourceIdentifier = SourceIdentifier(
      identifierType = IdentifierType.METS,
      ontologyType = "Work",
      value = bibNumber
    )

    val url = s"https://iiif.wellcomecollection.org/presentation/v2/$bibNumber"
    val digitalLocation = DigitalLocation(
      url = url,
      locationType = LocationType.IIIFPresentationAPI,
      license = Some(License.CCBYNC)
    )

    val createdDate = Instant.now()

    val unidentifiableItem =
      Item(id = IdState.Unidentifiable, locations = List(digitalLocation))
    metsData.toWork(version, createdDate).right.get shouldBe Work
      .Invisible[Source](
        version = version,
        state = Source(expectedSourceIdentifier, createdDate),
        data = Some(
          WorkData[DataState.Unidentified](
            items = List(unidentifiableItem),
            mergeCandidates = List(
              MergeCandidate(
                identifier = SourceIdentifier(
                  identifierType = IdentifierType.SierraSystemNumber,
                  ontologyType = "Work",
                  value = bibNumber
                ),
                reason = "METS work"
              )
            )
          )
        ),
        invisibilityReasons = List(MetsWorksAreNotVisible)
      )
  }

  it("creates a deleted work") {
    val bibNumber = createBibNumber
    val metsData =
      MetsData(recordIdentifier = bibNumber, deleted = true)
    val version = 1
    val expectedSourceIdentifier = SourceIdentifier(
      identifierType = IdentifierType.METS,
      ontologyType = "Work",
      value = bibNumber
    )

    val createdDate = Instant.now()

    metsData.toWork(version, createdDate).right.get shouldBe Work
      .Deleted[Source](
        version = version,
        state = Source(expectedSourceIdentifier, createdDate),
        deletedReason = DeletedFromSource("Mets")
      )

  }

  it("creates a invisible work with an item and no license") {
    val bibNumber = createBibNumber
    val metsData =
      MetsData(recordIdentifier = bibNumber, accessConditionDz = None)
    val version = 1
    val expectedSourceIdentifier = SourceIdentifier(
      identifierType = IdentifierType.METS,
      ontologyType = "Work",
      value = bibNumber
    )

    val url = s"https://iiif.wellcomecollection.org/presentation/v2/$bibNumber"
    val digitalLocation =
      DigitalLocation(
        url = url,
        locationType = LocationType.IIIFPresentationAPI,
        license = None
      )

    val createdDate = Instant.now()

    val unidentifiableItem =
      Item(id = IdState.Unidentifiable, locations = List(digitalLocation))
    metsData.toWork(version, createdDate).right.get shouldBe Work
      .Invisible[Source](
        version = version,
        state = Source(expectedSourceIdentifier, createdDate),
        data = Some(
          WorkData[DataState.Unidentified](
            items = List(unidentifiableItem),
            mergeCandidates = List(
              MergeCandidate(
                identifier = SourceIdentifier(
                  identifierType = IdentifierType.SierraSystemNumber,
                  ontologyType = "Work",
                  value = bibNumber
                ),
                reason = "METS work"
              )
            )
          )
        ),
        invisibilityReasons = List(MetsWorksAreNotVisible)
      )
  }

  it("fails creating a work if it cannot parse the license") {
    val bibNumber = createBibNumber
    val metsData =
      MetsData(recordIdentifier = bibNumber, accessConditionDz = Some("blah"))
    val version = 1

    metsData.toWork(version, Instant.now()).left.get shouldBe a[Exception]

  }

  it("can create a license if it matches the license label lowercase") {
    val metsData =
      MetsData(
        recordIdentifier = createBibNumber,
        accessConditionDz = Some("in copyright")
      )

    inside(metsData.toWork(1, Instant.now()).right.get.data.items) {
      case List(
          Item(
            IdState.Unidentifiable,
            _,
            _,
            List(DigitalLocation(_, _, license, _, _, _))
          )
          ) =>
        license shouldBe Some(License.InCopyright)
    }
  }

  it("can create a license if it matches the license label") {
    val metsData =
      MetsData(
        recordIdentifier = createBibNumber,
        accessConditionDz = Some("In copyright")
      )

    inside(metsData.toWork(1, Instant.now()).right.get.data.items) {
      case List(
          Item(
            IdState.Unidentifiable,
            _,
            _,
            List(DigitalLocation(_, _, license, _, _, _))
          )
          ) =>
        license shouldBe Some(License.InCopyright)
    }
  }

  it("can create a license if it matches the license url") {
    val metsData =
      MetsData(
        recordIdentifier = createBibNumber,
        accessConditionDz = Some(License.InCopyright.url)
      )
    inside(metsData.toWork(1, Instant.now()).right.get.data.items) {
      case List(
          Item(
            IdState.Unidentifiable,
            _,
            _,
            List(DigitalLocation(_, _, license, _, _, _))
          )
          ) =>
        license shouldBe Some(License.InCopyright)
    }
  }

  it("maps Copyright not cleared to In copyright") {
    val metsData = MetsData(
      recordIdentifier = createBibNumber,
      accessConditionDz = Some("Copyright not cleared")
    )
    val result = metsData.toWork(1, Instant.now())

    inside(result.right.get.data.items) {
      case List(
          Item(
            IdState.Unidentifiable,
            _,
            _,
            List(DigitalLocation(_, _, license, _, _, _))
          )
          ) =>
        license shouldBe Some(License.InCopyright)
    }
  }

  it("can create a license for rightsstatements.org/page/InC/1.0/?language=en") {
    val metsData =
      MetsData(
        recordIdentifier = createBibNumber,
        accessConditionDz =
          Some("rightsstatements.org/page/InC/1.0/?language=en")
      )
    val result = metsData.toWork(1, Instant.now())

    inside(result.right.get.data.items) {
      case List(
          Item(
            IdState.Unidentifiable,
            _,
            _,
            List(DigitalLocation(_, _, license, _, _, _))
          )
          ) =>
        license shouldBe Some(License.InCopyright)
    }
  }

  it("maps All Rights Reserved to In Copyright license") {
    val metsData =
      MetsData(
        recordIdentifier = createBibNumber,
        accessConditionDz = Some("All Rights Reserved")
      )
    val result = metsData.toWork(1, Instant.now())

    inside(result.right.get.data.items) {
      case List(
          Item(
            IdState.Unidentifiable,
            _,
            _,
            List(DigitalLocation(_, _, license, _, _, _))
          )
          ) =>
        license shouldBe Some(License.InCopyright)
    }
  }

  it("creates a invisible work with a thumbnail location") {
    val metsData = MetsData(
      recordIdentifier = createBibNumber,
      accessConditionDz = Some("CC-BY-NC"),
      fileReferencesMapping = List(
        "id" -> FileReference("l", "location.jp2", Some("image/jp2"))
      )
    )
    val result = metsData.toWork(1, Instant.now())
    result shouldBe a[Right[_, _]]
    result.right.get.data.thumbnail shouldBe Some(
      DigitalLocation(
        url =
          "https://iiif.wellcomecollection.org/thumbs/location.jp2/full/!200,200/0/default.jpg",
        locationType = LocationType.ThumbnailImage,
        license = Some(License.CCBYNC)
      )
    )
  }

  it("creates a invisible work with a title page thumbnail") {
    val metsData = MetsData(
      recordIdentifier = createBibNumber,
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
      DigitalLocation(
        url =
          "https://iiif.wellcomecollection.org/thumbs/title.jp2/full/!200,200/0/default.jpg",
        locationType = LocationType.ThumbnailImage,
        license = Some(License.CCBYNC)
      )
    )
  }

  it("creates a invisible work without a thumbnail for restricted works") {
    val metsData = MetsData(
      recordIdentifier = createBibNumber,
      accessConditionDz = Some("CC-BY-NC"),
      accessConditionStatus = Some("Restricted files"),
      fileReferencesMapping = List(
        "id" -> FileReference("l", "location.jp2", Some("image/jp2"))
      )
    )
    val result = metsData.toWork(1, Instant.now())
    result shouldBe a[Right[_, _]]
    result.right.get.data.thumbnail shouldBe None
  }

  it("serves the thumbnail from wellcomelibrary for PDFs") {
    val bibNumber = createBibNumber
    val metsData = MetsData(
      recordIdentifier = bibNumber,
      accessConditionDz = Some("CC-BY-NC"),
      fileReferencesMapping = List(
        "id" -> FileReference("l", "location.pdf")
      )
    )
    val result = metsData.toWork(1, Instant.now())
    result shouldBe a[Right[_, _]]
    result.right.get.data.thumbnail shouldBe Some(
      DigitalLocation(
        url = s"https://iiif.wellcomecollection.org/thumb/$bibNumber",
        locationType = LocationType.ThumbnailImage,
        license = Some(License.CCBYNC)
      )
    )
  }

  it("does not add a thumbnail if the file is a video") {
    val metsData = MetsData(
      recordIdentifier = createBibNumber,
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
      recordIdentifier = createBibNumber,
      accessConditionDz = Some("CC-BY-NC"),
      fileReferencesMapping = List(
        "id" -> FileReference("v", "video.mp3", Some("audio/x-mpeg-3"))
      )
    )
    val result = metsData.toWork(1, Instant.now())
    result shouldBe a[Right[_, _]]
    result.right.get.data.thumbnail shouldBe None
  }

  it("uses both the IIIF manifest and image for imageData locations") {
    val metsData = MetsData(
      recordIdentifier = createBibNumber,
      accessConditionDz = Some("CC-BY-NC"),
      fileReferencesMapping = List(
        "id" -> FileReference("l", "location.jp2", Some("image/jp2"))
      )
    )
    val result = metsData.toWork(1, Instant.now())
    result shouldBe a[Right[_, _]]
    result.right.get.data.imageData.head.locations shouldBe List(
      DigitalLocation(
        url =
          s"https://iiif.wellcomecollection.org/image/location.jp2/info.json",
        locationType = LocationType.IIIFImageAPI,
        license = Some(License.CCBYNC)
      ),
      DigitalLocation(
        url =
          s"https://iiif.wellcomecollection.org/presentation/v2/${metsData.recordIdentifier}",
        locationType = LocationType.IIIFPresentationAPI,
        license = Some(License.CCBYNC)
      )
    )
  }

  it("creates a work with a single accessCondition") {
    val result = MetsData(
      recordIdentifier = createBibNumber,
      accessConditionStatus = Some("Requires registration")
    ).toWork(1, Instant.now())
    result shouldBe a[Right[_, _]]
    inside(result.right.get.data.items.head.locations.head) {
      case DigitalLocation(_, _, _, _, _, accessConditions) =>
        accessConditions shouldBe List(
          AccessCondition(
            method = AccessMethod.ViewOnline,
            status = Some(AccessStatus.OpenWithAdvisory)
          )
        )

    }
  }

  it("creates a work with all images") {
    val bibNumber = createBibNumber
    val result = MetsData(
      recordIdentifier = bibNumber,
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
    val images = result.right.get.data.imageData
    images should have length 3
    images.map(_.id.allSourceIdentifiers.head.value) should contain theSameElementsAs List(
      s"$bibNumber/A",
      s"$bibNumber/B",
      s"$bibNumber/C"
    )
  }

  it("creates a work without images for restricted access statuses") {
    val result = MetsData(
      recordIdentifier = createBibNumber,
      accessConditionDz = Some("CC-BY-NC"),
      accessConditionStatus = Some("Restricted files"),
      fileReferencesMapping = List(
        "A" -> FileReference("A", "location1.jp2", Some("image/jp2")),
        "B" -> FileReference("B", "location2.jp2", Some("image/jp2"))
      )
    ).toWork(1, Instant.now())
    result shouldBe a[Right[_, _]]
    result.right.get.data.imageData shouldBe empty
  }

  it("creates a work with a single accessCondition including usage terms") {
    val result = MetsData(
      recordIdentifier = createBibNumber,
      accessConditionStatus = Some("Clinical images"),
      accessConditionUsage = Some("Please ask nicely")
    ).toWork(1, Instant.now())
    result shouldBe a[Right[_, _]]
    inside(result.right.get.data.items.head.locations.head) {
      case DigitalLocation(_, _, _, _, _, accessConditions) =>
        accessConditions shouldBe List(
          AccessCondition(
            method = AccessMethod.ViewOnline,
            status = Some(AccessStatus.Restricted),
            terms = Some("Please ask nicely")
          )
        )
    }
  }

  it("does not add access condition if all fields are empty") {
    val result = MetsData(
      recordIdentifier = createBibNumber,
      accessConditionStatus = None,
      accessConditionUsage = None
    ).toWork(1, Instant.now())
    result shouldBe a[Right[_, _]]
    inside(result.right.get.data.items.head.locations.head) {
      case DigitalLocation(_, _, _, _, _, accessConditions) =>
        accessConditions shouldBe List()
    }
  }

  it("maps restricted files to Restricted AccessCondition") {
    val result = MetsData(
      recordIdentifier = createBibNumber,
      accessConditionStatus = Some("Restricted files")
    ).toWork(1, Instant.now())
    result shouldBe a[Right[_, _]]
    inside(result.right.get.data.items.head.locations.head) {
      case DigitalLocation(_, _, _, _, _, accessConditions) =>
        accessConditions shouldBe
          List(
            AccessCondition(
              method = AccessMethod.ViewOnline,
              status = AccessStatus.Restricted
            )
          )
    }
  }

  it("fails creating a work when unknown AccessStatus") {
    val result = MetsData(
      recordIdentifier = createBibNumber,
      accessConditionStatus = Some("Kanye West")
    ).toWork(1, Instant.now())
    result shouldBe a[Left[_, _]]
  }

  it("lowercases the b number") {
    val data = MetsData(
      recordIdentifier = "B12345678"
    )

    val work = data.toWork(version = 1, modifiedTime = Instant.now()).value

    work.sourceIdentifier shouldBe SourceIdentifier(
      identifierType = IdentifierType.METS,
      ontologyType = "Work",
      value = "b12345678"
    )

    val mergeCandidates = work.data.mergeCandidates
    mergeCandidates should have size 1

    mergeCandidates.head.id.sourceIdentifier shouldBe
      SourceIdentifier(
        identifierType = IdentifierType.SierraSystemNumber,
        ontologyType = "Work",
        value = "b12345678"
      )
  }
}
