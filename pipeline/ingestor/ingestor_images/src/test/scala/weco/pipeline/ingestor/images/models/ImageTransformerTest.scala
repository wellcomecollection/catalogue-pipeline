package weco.pipeline.ingestor.images.models

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.LoneElement
import weco.catalogue.internal_model.generators.{
  IdentifiersGenerators,
  ImageGenerators
}
import weco.catalogue.internal_model.work.generators.{
  ItemsGenerators,
  WorkGenerators
}
import weco.catalogue.internal_model.locations.{License, LocationType}
import weco.pipeline.ingestor.images.ImageTransformer

import java.time.Instant

class ImageTransformerTest
    extends AnyFunSpec
    with Matchers
    with IdentifiersGenerators
    with WorkGenerators
    with ImageGenerators
    with ItemsGenerators
    with LoneElement {
  private lazy val imageTransformer = new ImageTransformer {
    override protected def getIndexedTime: Instant =
      Instant.parse("2001-01-01T01:01:01.00Z")
  }

  private def imageWithLicence(licence: Option[License]) =
    createImageDataWith(
      locations = List(
        createDigitalLocationWith(
          locationType = LocationType.IIIFImageAPI,
          license = licence
        )
      )
    )

  private def workWithLicence(licence: Option[License]) =
    identifiedWork()
      .items(
        List(
          createIdentifiedItemWith(
            locations = List(
              createDigitalLocationWith(license = licence)
            )
          )
        )
      )

  describe("licence fields") {
    // Images may have a licence, and may also get their licence information
    // from the parent Work.
    it("populates from the parent work") {
      val work = workWithLicence(Some(License.CCBYNCSA))
      val img = imageWithLicence(None).toAugmentedImageWith(parentWork = work)

      val derived = imageTransformer.deriveData(img)
      derived.aggregatableValues.licenses.loneElement should include(
        "cc-by-nc-sa"
      )
      derived.query.licenseIds.loneElement shouldBe "cc-by-nc-sa"
    }

    it("populates from the image itself") {
      val img = imageWithLicence(Some(License.CCBYNCSA)).toAugmentedImage
      val derived = imageTransformer.deriveData(img)
      derived.aggregatableValues.licenses.loneElement should include(
        "cc-by-nc-sa"
      )
      derived.query.licenseIds.loneElement shouldBe "cc-by-nc-sa"
    }

    it("populates from the image and its parent work combined") {
      val work = workWithLicence(Some(License.CCBYNCSA))

      val img = imageWithLicence(Some(License.PDM))
        .toAugmentedImageWith(parentWork = work)

      val derived = imageTransformer.deriveData(img)
      derived.aggregatableValues.licenses.head should include(
        "cc-by-nc-sa"
      )
      derived.aggregatableValues.licenses(1) should include(
        "pdm"
      )
      derived.query.licenseIds should contain theSameElementsAs Seq(
        "cc-by-nc-sa",
        "pdm"
      )

    }
  }
}
