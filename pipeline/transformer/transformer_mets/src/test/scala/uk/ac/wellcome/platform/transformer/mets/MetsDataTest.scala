package uk.ac.wellcome.platform.transformer.mets

import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.models.generators.RandomStrings
import uk.ac.wellcome.models.work.internal.{DigitalLocation, IdentifierType, Item, License_CCBYNC, LocationType, MaybeDisplayable, SourceIdentifier, Unidentifiable, UnidentifiedInvisibleWork, WorkData}

class MetsDataTest extends FunSpec with RandomStrings with Matchers{

  it("creates a invisible work with an item and a license"){
    val bNumber = randomAlphanumeric(10)
    val metsData = MetsData(recordIdentifier = bNumber, accessCondition = Some("CC-BY-NC"))
    val version = 1
    val expectedSourceIdentifier = SourceIdentifier(IdentifierType("mets","mets"), ontologyType = "Work", value = bNumber)

    val url = s"https://wellcomelibrary.org/iiif/$bNumber/manifest"
    val digitalLocation = DigitalLocation(url,LocationType("iiif-presentation"),license = Some(License_CCBYNC))

    val unidentifiableItem: MaybeDisplayable[Item] = Unidentifiable(Item(locations = List(digitalLocation)))
    metsData.toWork(version) shouldBe UnidentifiedInvisibleWork(
      version = version,
      sourceIdentifier = expectedSourceIdentifier,
      WorkData(items = List(unidentifiableItem)
      )
    )
  }

  it("creates a invisible work with an item and no license"){
    val bNumber = randomAlphanumeric(10)
    val metsData = MetsData(recordIdentifier = bNumber, accessCondition = None)
    val version = 1
    val expectedSourceIdentifier = SourceIdentifier(IdentifierType("mets","mets"), ontologyType = "Work", value = bNumber)

    val url = s"https://wellcomelibrary.org/iiif/$bNumber/manifest"
    val digitalLocation = DigitalLocation(url,LocationType("iiif-presentation"),license = None)

    val unidentifiableItem: MaybeDisplayable[Item] = Unidentifiable(Item(locations = List(digitalLocation)))
    metsData.toWork(version) shouldBe UnidentifiedInvisibleWork(
      version = version,
      sourceIdentifier = expectedSourceIdentifier,
      WorkData(items = List(unidentifiableItem)
      )
    )
  }

  it("fails creating a work if it cannot parse the license") {
    val bNumber = randomAlphanumeric(10)
    val metsData = MetsData(recordIdentifier = bNumber, accessCondition = Some("blah"))
    val version = 1

    intercept[ShouldNotTransformException]{
      metsData.toWork(version)
    }
  }

}

class ShouldNotTransformException(message: String)
  extends RuntimeException(message)
