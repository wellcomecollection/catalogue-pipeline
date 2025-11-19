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
  GoobiMetsGenerators,
  MetsDataGenerators
}
import weco.pipeline.transformer.mets.transformer.models.FileReference
import weco.pipeline.transformer.mets.transformers.ModsAccessConditions

class MetsDataTest
  extends AnyFunSpec
    with Matchers
    with EitherValues
    with Inside
    with MetsDataGenerators
    with GoobiMetsGenerators {

  it("creates a invisible work with an item and a license") {
    val bibNumber = createBibNumberString
    val title = randomAlphanumeric()
    val version = 1
    val modifiedTime = Instant.now()

    val metsData =
      createMetsDataWith(
        bibNumber = bibNumber,
        title = title,
        accessConditionDz = Some("CC-BY-NC"),
        version = version,
        modifiedTime = modifiedTime,
      )

    val expectedSourceIdentifier = SourceIdentifier(
      identifierType = IdentifierType.METS,
      ontologyType = "Work",
      value = bibNumber
    )

    val url = s"https://iiif.wellcomecollection.org/presentation/v2/$bibNumber"
    val digitalLocation = DigitalLocation(
      url = url,
      locationType = LocationType.IIIFPresentationAPI,
      license = Some(License.CCBYNC),
    )

    val unidentifiableItem =
      Item(id = IdState.Unidentifiable, locations = List(digitalLocation))
    metsData.toWork shouldBe Work
      .Invisible[Source](
        version = version,
        state = Source(
          sourceIdentifier = expectedSourceIdentifier,
          sourceModifiedTime = modifiedTime,
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
    val version = 1
    val modifiedTime = Instant.now()
    val metsData = DeletedMetsData(
      recordIdentifier = bibNumber,
      version = version,
      modifiedTime = modifiedTime
    )
    val expectedSourceIdentifier = SourceIdentifier(
      identifierType = IdentifierType.METS,
      ontologyType = "Work",
      value = bibNumber
    )

    metsData.toWork shouldBe Work
      .Deleted[Source](
        version = version,
        state = Source(expectedSourceIdentifier, modifiedTime),
        deletedReason = DeletedFromSource("Mets")
      )
  }

  it("creates a invisible work with an item and no license") {
    val bibNumber = createBibNumberString
    val title = randomAlphanumeric()
    val modifiedTime = Instant.now()

    val metsData = createMetsDataWith(
      bibNumber = bibNumber,
      title = title,
      accessConditionDz = None,
      version = 1,
      modifiedTime = modifiedTime,
    )
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
        license = None,
      )

    val unidentifiableItem =
      Item(id = IdState.Unidentifiable, locations = List(digitalLocation))
    metsData.toWork shouldBe Work
      .Invisible[Source](
        version = 1,
        state = Source(
          sourceIdentifier = expectedSourceIdentifier,
          sourceModifiedTime = modifiedTime,
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
    ModsAccessConditions(
      dz = Some("blah"),
      status = None,
      usage = None
    ).parse.left.get shouldBe a[Exception]

  }

  it("can create a license if it matches the license label lowercase") {
    val metsData = createMetsDataWith(
      accessConditionDz = Some("in copyright")
    )

    inside(metsData.toWork.data.items) {
      case List(
      Item(
      IdState.Unidentifiable,
      _,
      _,
      List(DigitalLocation(_, _, license, _, _, _, _))
      )
      ) =>
        license shouldBe Some(License.InCopyright)
    }
  }

  it("can create a license if it matches the license label") {
    val metsData = createMetsDataWith(
      accessConditionDz = Some("In copyright")
    )

    inside(metsData.toWork.data.items) {
      case List(
      Item(
      IdState.Unidentifiable,
      _,
      _,
      List(DigitalLocation(_, _, license, _, _, _, _))
      )
      ) =>
        license shouldBe Some(License.InCopyright)
    }
  }

  it("can create a license if it matches the license url") {
    val metsData = createMetsDataWith(
      accessConditionDz = Some(License.InCopyright.url)
    )
    inside(metsData.toWork.data.items) {
      case List(
      Item(
      IdState.Unidentifiable,
      _,
      _,
      List(DigitalLocation(_, _, license, _, _, _, _))
      )
      ) =>
        license shouldBe Some(License.InCopyright)
    }
  }

  it("maps Copyright not cleared to In copyright") {
    val metsData = createMetsDataWith(
      accessConditionDz = Some("Copyright not cleared")
    )
    val result = metsData.toWork

    inside(result.data.items) {
      case List(
      Item(
      IdState.Unidentifiable,
      _,
      _,
      List(DigitalLocation(_, _, license, _, _, _, _))
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
    val result = metsData.toWork

    inside(result.data.items) {
      case List(
      Item(
      IdState.Unidentifiable,
      _,
      _,
      List(DigitalLocation(_, _, license, _, _, _, _))
      )
      ) =>
        license shouldBe Some(License.InCopyright)
    }
  }

  it("maps All Rights Reserved to In Copyright license") {
    val metsData = createMetsDataWith(
      accessConditionDz = Some("All Rights Reserved")
    )
    val result = metsData.toWork

    inside(result.data.items) {
      case List(
      Item(
      IdState.Unidentifiable,
      _,
      _,
      List(DigitalLocation(_, _, license, _, _, _, _))
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
    val result = metsData.toWork
    result.data.thumbnail shouldBe Some(
      DigitalLocation(
        url =
          s"https://iiif.wellcomecollection.org/thumbs/${metsData.recordIdentifier}_location.jp2/full/!200,200/0/default.jpg",
        locationType = LocationType.ThumbnailImage,
        license = Some(License.CCBYNC),
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
    val result = metsData.toWork
    result.data.thumbnail shouldBe Some(
      DigitalLocation(
        url =
          s"https://iiif.wellcomecollection.org/thumbs/${metsData.recordIdentifier}_title.jp2/full/!200,200/0/default.jpg",
        locationType = LocationType.ThumbnailImage,
        license = Some(License.CCBYNC),
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
    val result = metsData.toWork
    result.data.thumbnail shouldBe None
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
    val result = metsData.toWork
    result.data.thumbnail shouldBe Some(
      DigitalLocation(
        url = s"https://iiif.wellcomecollection.org/thumb/$bibNumber",
        locationType = LocationType.ThumbnailImage,
        license = Some(License.CCBYNC),
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
    val result = metsData.toWork
    result.data.thumbnail shouldBe None
  }

  it("does not add a thumbnail if the file is an audio") {
    val metsData = createMetsDataWith(
      accessConditionDz = Some("CC-BY-NC"),
      fileReferences = List(
        FileReference("v", "video.mp3", Some("audio/x-mpeg-3"))
      )
    )
    val result = metsData.toWork
    result.data.thumbnail shouldBe None
  }

  it("uses both the IIIF manifest and image for imageData locations") {
    val metsData = createMetsDataWith(
      accessConditionDz = Some("CC-BY-NC"),
      fileReferences = List(
        FileReference("l", "location.jp2", Some("image/jp2"))
      )
    )
    val result = metsData.toWork
    result.data.imageData.head.locations shouldBe List(
      DigitalLocation(
        url =
          s"https://iiif.wellcomecollection.org/image/${metsData.recordIdentifier}_location.jp2/info.json",
        locationType = LocationType.IIIFImageAPI,
        license = Some(License.CCBYNC),
      ),
      DigitalLocation(
        url =
          s"https://iiif.wellcomecollection.org/presentation/v2/${metsData.recordIdentifier}",
        locationType = LocationType.IIIFPresentationAPI,
        license = Some(License.CCBYNC),
      )
    )
  }

  it("creates a work with a single accessCondition") {
    val metsData = createMetsDataWith(
      accessConditionStatus = Some("Requires registration")
    )

    val result = metsData.toWork
    inside(result.data.items.head.locations.head) {
      case DigitalLocation(_, _, _, _, _, accessConditions, _) =>
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

    val result = metsData.toWork
    val images = result.data.imageData
    images should have length 3
    images.map(
      _.id.allSourceIdentifiers.head.value
    ) should contain theSameElementsAs List(
      s"$bibNumber/A",
      s"$bibNumber/B",
      s"$bibNumber/C"
    )
  }

  it("normalises image locations for use with DLCS") {
    val bibNumber = createBibNumberString
    val metsData = createMetsDataWith(
      bibNumber = bibNumber,
      accessConditionDz = Some("CC-BY-NC"),
      fileReferences = List(
        FileReference("A", "location1.jp2", Some("image/jp2")),
        FileReference("B", "objects/location2.jp2", Some("image/jp2"))
      )
    )

    val result = metsData.toWork
    val images = result.data.imageData
    images.map(
      _.locations.head.url
    ) should contain theSameElementsAs List(
      s"https://iiif.wellcomecollection.org/image/${bibNumber}_location1.jp2/info.json",
      s"https://iiif.wellcomecollection.org/image/${bibNumber}_location2.jp2/info.json"
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
    val result = metsData.toWork
    result.data.imageData shouldBe empty
  }

  it("creates a work with a single accessCondition including usage terms") {
    val metsData = createMetsDataWith(
      accessConditionStatus = Some("Clinical images"),
      accessConditionUsage = Some("Please ask nicely")
    )
    val result = metsData.toWork
    inside(result.data.items.head.locations.head) {
      case DigitalLocation(_, _, _, _, _, accessConditions, _) =>
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
    val result = metsData.toWork
    inside(result.data.items.head.locations.head) {
      case DigitalLocation(_, _, _, _, _, accessConditions, _) =>
        accessConditions shouldBe List()
    }
  }

  it("maps restricted files to Restricted AccessCondition") {
    val metsData = createMetsDataWith(
      accessConditionStatus = Some("Restricted files")
    )
    val result = metsData.toWork
    inside(result.data.items.head.locations.head) {
      case DigitalLocation(_, _, _, _, _, accessConditions, _) =>
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
    ModsAccessConditions(
      dz = None,
      status = Some("Kanye West"),
      usage = None
    ).parse shouldBe a[Left[_, _]]
  }

  it("lowercases the b number") {
    val metsData = createMetsDataWith(bibNumber = "B12345678")

    val work = metsData.toWork

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
