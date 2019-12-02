package uk.ac.wellcome.platform.transformer.mets.transformer

import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.models.generators.RandomStrings
import uk.ac.wellcome.models.work.internal._

class MetsTest extends FunSpec with RandomStrings with Matchers {

  it("creates a invisible work with an item and a license") {
    val bNumber = randomAlphanumeric(10)
    val metsData =
      Mets(recordIdentifier = bNumber, accessCondition = Some("CC-BY-NC"))
    val version = 1
    val expectedSourceIdentifier = SourceIdentifier(
      IdentifierType("mets", "METS"),
      ontologyType = "Work",
      value = bNumber)

    val url = s"https://wellcomelibrary.org/iiif/$bNumber/manifest"
    val digitalLocation = DigitalLocation(
      url,
      LocationType("iiif-presentation"),
      license = Some(License_CCBYNC))

    val unidentifiableItem: MaybeDisplayable[Item] =
      Unidentifiable(Item(locations = List(digitalLocation)))
    metsData.toWork(version).right.get shouldBe UnidentifiedInvisibleWork(
      version = version,
      sourceIdentifier = expectedSourceIdentifier,
      WorkData(
        items = List(unidentifiableItem),
        mergeCandidates = List(
          MergeCandidate(
            identifier = SourceIdentifier(
              identifierType = IdentifierType("sierra-system-number"),
              ontologyType = "Work",
              value = bNumber
            ),
            reason = Some("METS work")
          )
        )
      )
    )
  }

  it("creates a invisible work with an item and no license") {
    val bNumber = randomAlphanumeric(10)
    val metsData = Mets(recordIdentifier = bNumber, accessCondition = None)
    val version = 1
    val expectedSourceIdentifier = SourceIdentifier(
      IdentifierType("mets", "METS"),
      ontologyType = "Work",
      value = bNumber)

    val url = s"https://wellcomelibrary.org/iiif/$bNumber/manifest"
    val digitalLocation =
      DigitalLocation(url, LocationType("iiif-presentation"), license = None)

    val unidentifiableItem: MaybeDisplayable[Item] =
      Unidentifiable(Item(locations = List(digitalLocation)))
    metsData.toWork(version).right.get shouldBe UnidentifiedInvisibleWork(
      version = version,
      sourceIdentifier = expectedSourceIdentifier,
      WorkData(
        items = List(unidentifiableItem),
        mergeCandidates = List(
          MergeCandidate(
            identifier = SourceIdentifier(
              identifierType = IdentifierType("sierra-system-number"),
              ontologyType = "Work",
              value = bNumber
            ),
            reason = Some("METS work")
          )
        )
      )
    )
  }

  it("fails creating a work if it cannot parse the license") {
    val bNumber = randomAlphanumeric(10)
    val metsData =
      Mets(recordIdentifier = bNumber, accessCondition = Some("blah"))
    val version = 1

    metsData.toWork(version).left.get shouldBe a[Exception]

  }

  it("creates a invisible work with a thumbnail location") {
    val metsData = Mets(
      recordIdentifier = randomAlphanumeric(10),
      accessCondition = Some("CC-BY-NC"),
      thumbnailLocation = Some("location.png")
    )
    val result = metsData.toWork(1)
    result shouldBe a[Right[_, _]]
    result.right.get.data.thumbnail shouldBe Some(
      DigitalLocation(
        s"https://dlcs.io/iiif-img/wellcome/5/location.png/full/,200/0/default.jpg",
        LocationType("thumbnail-image")
      )
    )
  }
}
