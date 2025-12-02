package weco.pipeline.merger.rules

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{Inspectors, OptionValues, PrivateMethodTester}
import weco.catalogue.internal_model.locations.{DigitalLocation, License}
import weco.catalogue.internal_model.work.generators.{
  MetsWorkGenerators,
  MiroWorkGenerators,
  SierraWorkGenerators
}
import weco.catalogue.internal_model.work.Format

class ImagesRuleTest
    extends AnyFunSpec
    with Matchers
    with MiroWorkGenerators
    with SierraWorkGenerators
    with MetsWorkGenerators
    with PrivateMethodTester
    with OptionValues
    with Inspectors {

  describe("image creation rules") {
    it("creates n images from n Miro works and a single Sierra work") {
      info(
        "normally, miro images are emitted as Images and become the imageData of a Sierra work"
      )
      val n = 3
      val miroWorks = (1 to n).map(_ => miroIdentifiedWork())
      val sierraWork = sierraDigitalIdentifiedWork()
      val result = ImageDataRule.merge(sierraWork, miroWorks.toList).data

      result should have length n
      result.map(_.locations) should contain theSameElementsAs
        miroWorks.map(_.data.imageData.head.locations)
    }

    it(
      "creates n images from a METS work containing n images, and a single Sierra picture work"
    ) {
      info(
        "METS images are emitted as Images for picture/ephemera Sierra works"
      )

      val n = 5
      val metsWork = createInvisibleMetsIdentifiedWorkWith(numImages = n)
      val sierraPictureWork = sierraIdentifiedWork().format(Format.Pictures)
      val result =
        ImagesRule.merge(sierraPictureWork, List(metsWork)).data

      result should have length n
      result.map(_.locations) should contain theSameElementsAs
        metsWork.data.imageData.map(_.locations)
    }

    it(
      "creates n images from a METS work containing n images, and a single Sierra ephemera work"
    ) {
      info(
        "METS images are emitted as Images for picture/ephemera Sierra works"
      )
      val n = 5
      val metsWork = createInvisibleMetsIdentifiedWorkWith(numImages = n)
      val sierraEphemeraWork = sierraIdentifiedWork().format(Format.Ephemera)
      val result =
        ImagesRule.merge(sierraEphemeraWork, List(metsWork)).data

      result should have length n
      result.map(_.locations) should contain theSameElementsAs
        metsWork.data.imageData.map(_.locations)
    }

    it(
      "creates n + m images from m Miro works, a METS work containing n images, and a single Sierra picture work"
    ) {
      info(
        "Both METS and Miro images are emitted as Images for picture/ephemera Sierra works"
      )
      val n = 3
      val m = 4
      val miroWorks = (1 to m).map(_ => miroIdentifiedWork()).toList
      val metsWork = createInvisibleMetsIdentifiedWorkWith(numImages = n)
      val sierraPictureWork = sierraIdentifiedWork().format(Format.Pictures)
      val result =
        ImagesRule.merge(sierraPictureWork, miroWorks :+ metsWork).data

      result should have length n + m
      result.map(_.locations) should contain theSameElementsAs
        metsWork.data.imageData.map(_.locations) ++
        miroWorks.map(_.data.imageData.head.locations)
    }

    it(
      "creates n + m images from m Miro works, a METS work containing n images, and a single Sierra ephemera work"
    ) {
      info(
        "Both METS and Miro images are emitted as Images for picture/ephemera Sierra works"
      )
      val n = 3
      val m = 4
      val miroWorks = (1 to m).map(_ => miroIdentifiedWork()).toList

      val metsWork = createInvisibleMetsIdentifiedWorkWith(
        numImages = n
      )

      val sierraEphemeraWork = sierraIdentifiedWork().format(Format.Ephemera)
      val result =
        ImagesRule.merge(sierraEphemeraWork, miroWorks :+ metsWork).data

      result should have length n + m
      result.map(_.locations) should contain theSameElementsAs
        metsWork.data.imageData.map(_.locations) ++ miroWorks.map(
          _.data.imageData.head.locations
        )
    }

    it(
      "overrides the licence in Miro works with the licence from the METS work"
    ) {
      val n = 3
      val m = 4
      val miroWorks = (1 to m).map(_ => miroIdentifiedWork()).toList
      val expectedMiroLocations: Seq[List[DigitalLocation]] = miroWorks map {
        work =>
          work.data.imageData flatMap {
            imageData =>
              imageData.locations.map(
                _.copy(license = Some(License.InCopyright))
              )
          }
      }
      val metsWork = createInvisibleMetsIdentifiedWorkWith(
        numImages = n,
        imageLocationLicence = Some(License.InCopyright)
      )
      val sierraEphemeraWork = sierraIdentifiedWork().format(Format.Ephemera)
      val result =
        ImagesRule.merge(sierraEphemeraWork, miroWorks :+ metsWork).data

      result should have length n + m
      result.map(_.locations) should contain theSameElementsAs
        metsWork.data.imageData.map(_.locations) ++ expectedMiroLocations
    }

    it(
      "ignores METS images, but uses n Miro images, for a non-picture/ephemera Sierra work"
    ) {
      val n = 3
      val metsWork = createInvisibleMetsIdentifiedWorkWith(numImages = 3)
      val miroWorks = (1 to n).map(_ => miroIdentifiedWork()).toList
      val sierraWork = sierraDigitalIdentifiedWork()
      val result = ImagesRule.merge(sierraWork, miroWorks :+ metsWork).data

      result should have length n
      result.map(_.locations) should contain theSameElementsAs
        miroWorks.map(_.data.imageData.head.locations)
    }
    describe("digmiro suppression") {
      it(
        "does not use Miro images when a METS image is present for a digaids Sierra work"
      ) {
        info(
          "digaids refers to records where Miro images have been redigitised via Mets"
        )
        val metsWork = createInvisibleMetsIdentifiedWorkWith(numImages = 1)
        val miroWork = miroIdentifiedWork()
        val sierraDigaidsWork = sierraIdentifiedWork()
          .format(Format.Pictures)
          .otherIdentifiers(List(createDigcodeIdentifier("digaids")))
        val result =
          ImagesRule
            .merge(sierraDigaidsWork, List(miroWork, metsWork))
            .data

        result should have length 1
        result.map(
          _.locations
        ) should contain theSameElementsAs metsWork.data.imageData
          .map(_.locations)
      }

      it(
        "does not use Miro images when a METS image is present for a digmiro Sierra work"
      ) {
        info(
          "digmiro refers to records where Miro images have been redigitised via Mets"
        )

        val metsWork = createInvisibleMetsIdentifiedWorkWith(numImages = 1)
        val miroWork = miroIdentifiedWork()
        val sierraDigmiroWork = sierraIdentifiedWork()
          .format(Format.Pictures)
          .otherIdentifiers(List(createDigcodeIdentifier("digmiro")))
        val result =
          ImagesRule.merge(sierraDigmiroWork, List(miroWork, metsWork)).data

        result should contain theSameElementsAs metsWork.data.imageData
      }
    }

    it(
      "retains miro images in the absence of digmiro/digaids"
    ) {
      val metsWork = createInvisibleMetsIdentifiedWorkWith(numImages = 1)
      val miroWork = miroIdentifiedWork()
      val sierraDigmiroWork = sierraIdentifiedWork()
        .format(Format.Pictures)
        .otherIdentifiers(
          List(
            createDigcodeIdentifier("digicon")
          )
        )
      val result =
        ImagesRule.merge(sierraDigmiroWork, List(miroWork, metsWork)).data

      result should have length 2

      result should contain theSameElementsAs metsWork.data.imageData ++ miroWork.data.imageData
    }

    it(
      "correctly identifies a digmiro Sierra work regardless of other digcodes"
    ) {
      val metsWork = createInvisibleMetsIdentifiedWorkWith(numImages = 1)
      val miroWork = miroIdentifiedWork()
      val sierraDigmiroWork = sierraIdentifiedWork()
        .format(Format.Pictures)
        .otherIdentifiers(
          List(
            createDigcodeIdentifier("CBM"),
            createDigcodeIdentifier("digmiro"),
            createDigcodeIdentifier("WOAM")
          )
        )
      val result =
        ImagesRule.merge(sierraDigmiroWork, List(miroWork, metsWork)).data

      result should contain theSameElementsAs (metsWork.data.imageData)
    }

    it(
      "correctly identifies a digmiro Sierra work even if there is an unmarked Sierra work participating in the merge"
    ) {
      val metsWork = createInvisibleMetsIdentifiedWorkWith(numImages = 1)
      val miroWork = miroIdentifiedWork()
      val sierraDigmiroWork = sierraIdentifiedWork()
        .format(Format.Pictures)
        .otherIdentifiers(
          List(
            createDigcodeIdentifier("digmiro")
          )
        )
      val result =
        ImagesRule
          .merge(
            sierraIdentifiedWork(),
            List(miroWork, sierraDigmiroWork, metsWork)
          )
          .data

      result should contain theSameElementsAs (metsWork.data.imageData)
    }
  }

}
