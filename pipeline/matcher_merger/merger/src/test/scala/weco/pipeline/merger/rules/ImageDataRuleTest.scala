package weco.pipeline.merger.rules

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.{Inspectors, OptionValues, PrivateMethodTester}
import weco.catalogue.internal_model.work.generators.SierraWorkGenerators
import weco.catalogue.internal_model.work.generators.{
  MetsWorkGenerators,
  MiroWorkGenerators,
  SierraWorkGenerators
}

class ImageDataRuleTest
    extends AnyFunSpec
    with Matchers
    with MiroWorkGenerators
    with SierraWorkGenerators
    with MetsWorkGenerators
    with PrivateMethodTester
    with OptionValues
    with Inspectors
    with TableDrivenPropertyChecks {

  describe("returning images for the imageData property") {
    info(
      "The imageData property of a Sierra merge target is only populated using Miro images."
    )
    info(
      "Images from other sources are instead linked to the target via the items property."
    )

    it("returns an empty list if there are no sources to merge from") {
      val sierraWork = sierraDigitalIdentifiedWork()
      ImageDataRule.merge(sierraWork, Nil).data shouldBe empty
    }

    it("returns images from Miro") {
      val sierraWork = sierraDigitalIdentifiedWork()
      val miroWorks = (1 to 5).map(_ => miroIdentifiedWork())
      ImageDataRule
        .merge(sierraWork, miroWorks)
        .data should contain theSameElementsAs miroWorks.flatMap(
        _.data.imageData
      )
    }

    it("does not return images from METS") {
      val miroWorks = (1 to 5).map(_ => miroIdentifiedWork())
      val metsWork = createInvisibleMetsIdentifiedWorkWith(numImages = 3)
      val sierraWork = sierraDigitalIdentifiedWork()
      val result = ImageDataRule.merge(sierraWork, miroWorks :+ metsWork).data

      result should have length 5
      result.map(_.locations) should contain theSameElementsAs
        miroWorks.map(_.data.imageData.head.locations)
    }

    describe("discarding overridden images in digmiro works") {
      info(
        "digmiro refers to records where Miro images have been redigitised via Mets"
      )
      info(
        "as such, the Miro images are to be ignored, as the Mets content replaces it"
      )
      info(
        "there are two digcodes that designate a Sierra document as digmiro - digmiro and digaids"
      )

      it(s"discards Miro images for Sierra works with digmiro or digaids") {
        forAll(Table("digcode", "digmiro", "digaids")) {
          digcode =>
            val sierraWork = sierraDigitalIdentifiedWork().otherIdentifiers(
              List(
                createDigcodeIdentifier(digcode)
              )
            )
            val miroWorks = (1 to 5).map(_ => miroIdentifiedWork())
            ImageDataRule
              .merge(sierraWork, miroWorks)
              .data shouldBe empty
        }
      }

      it(s"returns Miro images for Sierra works with non-digmiro digcodes") {
        val sierraWork = sierraDigitalIdentifiedWork().otherIdentifiers(
          List(
            createDigcodeIdentifier("digicon")
          )
        )
        val miroWorks = (1 to 5).map(_ => miroIdentifiedWork())
        ImageDataRule
          .merge(sierraWork, miroWorks)
          .data should contain theSameElementsAs miroWorks.flatMap(
          _.data.imageData
        )
      }

      it(
        s"discards Miro images for Sierra works with a mixture of digmiro and non-digmiro digcodes"
      ) {
        val sierraWork = sierraDigitalIdentifiedWork().otherIdentifiers(
          List(
            createDigcodeIdentifier("digicon"),
            createDigcodeIdentifier("digmiro"),
            createDigcodeIdentifier("digpicture")
          )
        )
        val miroWorks = (1 to 5).map(_ => miroIdentifiedWork())
        ImageDataRule
          .merge(sierraWork, miroWorks)
          .data shouldBe empty
      }
    }
  }
}
