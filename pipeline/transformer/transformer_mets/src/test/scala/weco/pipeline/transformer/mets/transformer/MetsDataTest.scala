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
import weco.pipeline.transformer.mets.generators.{
  MetsDataGenerators,
  MetsGenerators
}
import weco.pipeline.transformer.mets.transformer.models.FileReference

class MetsDataTest
    extends AnyFunSpec
    with Matchers
    with EitherValues
    with Inside
    with MetsDataGenerators
    with MetsGenerators {

  it("creates a invisible work with an item and a license") {
    val bibNumber = createBibNumberString
    val title = randomAlphanumeric()

    val metsData =
      createMetsDataWith(
        bibNumber = bibNumber,
        title = title,
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
        state = Source(
          sourceIdentifier = expectedSourceIdentifier,
          sourceModifiedTime = createdDate,
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
        ),
        data = WorkData[DataState.Unidentified](
          title = Some(title),
          items = List(unidentifiableItem)
        ),
        invisibilityReasons = List(MetsWorksAreNotVisible)
      )
  }

  it("creates a deleted work") {
    val bibNumber = createBibNumberString
    val metsData = DeletedMetsData(bibNumber)
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
    val bibNumber = createBibNumberString
    val title = randomAlphanumeric()
    val metsData = createMetsDataWith(
      bibNumber = bibNumber,
      title = title,
      accessConditionDz = None
    )
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
        state = Source(
          sourceIdentifier = expectedSourceIdentifier,
          sourceModifiedTime = createdDate,
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
        ),
        data = WorkData[DataState.Unidentified](
          title = Some(title),
          items = List(unidentifiableItem)
        ),
        invisibilityReasons = List(MetsWorksAreNotVisible)
      )
  }

  it("fails creating a work if it cannot parse the license") {
    val metsData = createMetsDataWith(accessConditionDz = Some("blah"))
    val version = 1

    metsData.toWork(version, Instant.now()).left.get shouldBe a[Exception]

  }

  it("can create a license if it matches the license label lowercase") {
    val metsData = createMetsDataWith(
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
    val metsData = createMetsDataWith(
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
    val metsData = createMetsDataWith(
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
    val metsData = createMetsDataWith(
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

  it(
    "can create a license for rightsstatements.org/page/InC/1.0/?language=en"
  ) {
    val metsData = createMetsDataWith(
      accessConditionDz = Some("rightsstatements.org/page/InC/1.0/?language=en")
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
    val metsData = createMetsDataWith(
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
    val metsData = createMetsDataWith(
      accessConditionDz = Some("CC-BY-NC"),
      thumbnailReference =
        Some(FileReference("l", "location.jp2", Some("image/jp2")))
    )
    val result = metsData.toWork(1, Instant.now())
    result shouldBe a[Right[_, _]]
    result.right.get.data.thumbnail shouldBe Some(
      DigitalLocation(
        url =
          s"https://iiif.wellcomecollection.org/thumbs/${metsData.recordIdentifier}_location.jp2/full/!200,200/0/default.jpg",
        locationType = LocationType.ThumbnailImage,
        license = Some(License.CCBYNC)
      )
    )
  }

  it("creates a invisible work with a title page thumbnail") {
    val metsData = createMetsDataWith(
      accessConditionDz = Some("CC-BY-NC"),
      fileReferences = List(
        FileReference("l", "location.jp2", Some("image/jp2")),
        FileReference("l", "title.jp2", Some("image/jp2"))
      ),
      thumbnailReference =
        Some(FileReference("l", "title.jp2", Some("image/jp2")))
    )
    val result = metsData.toWork(1, Instant.now())
    result shouldBe a[Right[_, _]]
    result.right.get.data.thumbnail shouldBe Some(
      DigitalLocation(
        url =
          s"https://iiif.wellcomecollection.org/thumbs/${metsData.recordIdentifier}_title.jp2/full/!200,200/0/default.jpg",
        locationType = LocationType.ThumbnailImage,
        license = Some(License.CCBYNC)
      )
    )
  }

  it("creates a invisible work without a thumbnail for restricted works") {
    val metsData = createMetsDataWith(
      accessConditionDz = Some("CC-BY-NC"),
      accessConditionStatus = Some("Restricted files"),
      fileReferences = List(
        FileReference("l", "location.jp2", Some("image/jp2"))
      )
    )
    val result = metsData.toWork(1, Instant.now())
    result shouldBe a[Right[_, _]]
    result.right.get.data.thumbnail shouldBe None
  }

  it("serves the thumbnail from wellcomelibrary for PDFs") {
    val bibNumber = createBibNumberString
    val metsData = createMetsDataWith(
      bibNumber = bibNumber,
      accessConditionDz = Some("CC-BY-NC"),
      fileReferences = List(
        FileReference("l", "location.pdf")
      ),
      thumbnailReference = Some(FileReference("l", "location.pdf"))
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
    val metsData = createMetsDataWith(
      accessConditionDz = Some("CC-BY-NC"),
      fileReferences = List(
        FileReference("v", "video.mpg", Some("video/mpeg"))
      )
    )
    val result = metsData.toWork(1, Instant.now())
    result shouldBe a[Right[_, _]]
    result.right.get.data.thumbnail shouldBe None
  }

  it("does not add a thumbnail if the file is an audio") {
    val metsData = createMetsDataWith(
      accessConditionDz = Some("CC-BY-NC"),
      fileReferences = List(
        FileReference("v", "video.mp3", Some("audio/x-mpeg-3"))
      )
    )
    val result = metsData.toWork(1, Instant.now())
    result shouldBe a[Right[_, _]]
    result.right.get.data.thumbnail shouldBe None
  }

  it("uses both the IIIF manifest and image for imageData locations") {
    val metsData = createMetsDataWith(
      accessConditionDz = Some("CC-BY-NC"),
      fileReferences = List(
        FileReference("l", "location.jp2", Some("image/jp2"))
      )
    )
    val result = metsData.toWork(1, Instant.now())
    result shouldBe a[Right[_, _]]
    result.right.get.data.imageData.head.locations shouldBe List(
      DigitalLocation(
        url =
          s"https://iiif.wellcomecollection.org/image/${metsData.recordIdentifier}_location.jp2/info.json",
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
    val metsData = createMetsDataWith(
      accessConditionStatus = Some("Requires registration")
    )

    val result = metsData.toWork(1, Instant.now())
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
    val bibNumber = createBibNumberString
    val metsData = createMetsDataWith(
      bibNumber = bibNumber,
      accessConditionDz = Some("CC-BY-NC"),
      fileReferences = List(
        FileReference("A", "location1.jp2", Some("image/jp2")),
        FileReference("B", "location2.jp2", Some("image/jp2")),
        FileReference("C", "location3.jp2", Some("image/jp2")),
        FileReference("D", "location4.jp2", Some("application/pdf")),
        FileReference("E", "location4.jp2", Some("video/mpeg"))
      )
    )

    val result = metsData.toWork(1, Instant.now())
    result shouldBe a[Right[_, _]]
    val images = result.right.get.data.imageData
    images should have length 3
    images.map(
      _.id.allSourceIdentifiers.head.value
    ) should contain theSameElementsAs List(
      s"$bibNumber/A",
      s"$bibNumber/B",
      s"$bibNumber/C"
    )
  }

  it("creates a work without images for restricted access statuses") {
    val metsData = createMetsDataWith(
      accessConditionDz = Some("CC-BY-NC"),
      accessConditionStatus = Some("Restricted files"),
      fileReferences = List(
        FileReference("A", "location1.jp2", Some("image/jp2")),
        FileReference("B", "location2.jp2", Some("image/jp2"))
      )
    )
    val result = metsData.toWork(1, Instant.now())
    result shouldBe a[Right[_, _]]
    result.right.get.data.imageData shouldBe empty
  }

  it("creates a work with a single accessCondition including usage terms") {
    val metsData = createMetsDataWith(
      accessConditionStatus = Some("Clinical images"),
      accessConditionUsage = Some("Please ask nicely")
    )
    val result = metsData.toWork(1, Instant.now())
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
    val metsData = createMetsDataWith(
      accessConditionStatus = None,
      accessConditionUsage = None
    )
    val result = metsData.toWork(1, Instant.now())
    result shouldBe a[Right[_, _]]
    inside(result.right.get.data.items.head.locations.head) {
      case DigitalLocation(_, _, _, _, _, accessConditions) =>
        accessConditions shouldBe List()
    }
  }

  it("maps restricted files to Restricted AccessCondition") {
    val metsData = createMetsDataWith(
      accessConditionStatus = Some("Restricted files")
    )
    val result = metsData.toWork(1, Instant.now())
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
    val metsData = createMetsDataWith(
      accessConditionStatus = Some("Kanye West")
    )
    val result = metsData.toWork(1, Instant.now())
    result shouldBe a[Left[_, _]]
  }

  it("lowercases the b number") {
    val metsData = createMetsDataWith(bibNumber = "B12345678")

    val work = metsData.toWork(version = 1, modifiedTime = Instant.now()).value

    work.sourceIdentifier shouldBe SourceIdentifier(
      identifierType = IdentifierType.METS,
      ontologyType = "Work",
      value = "b12345678"
    )

    val mergeCandidates = work.state.mergeCandidates
    mergeCandidates should have size 1

    mergeCandidates.head.id.sourceIdentifier shouldBe
      SourceIdentifier(
        identifierType = IdentifierType.SierraSystemNumber,
        ontologyType = "Work",
        value = "b12345678"
      )
  }
}
