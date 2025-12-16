package weco.pipeline.merger.rules

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.{OptionValues, PrivateMethodTester}
import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.image.ImageData
import weco.catalogue.internal_model.locations.{DigitalLocation, License}
import weco.catalogue.internal_model.work.WorkState.Identified
import weco.catalogue.internal_model.work.generators.{
  MetsWorkGenerators,
  MiroWorkGenerators,
  SierraWorkGenerators
}
import weco.catalogue.internal_model.work.{Format, Work}

class ImagesRuleTest
    extends AnyFunSpec
    with Matchers
    with MiroWorkGenerators
    with SierraWorkGenerators
    with MetsWorkGenerators
    with PrivateMethodTester
    with OptionValues
    with TableDrivenPropertyChecks {

  case class ImagesAndImageData(
    images: List[ImageData[IdState.Identified]],
    imageData: List[ImageData[IdState.Identified]]
  )

  def imagesAndImageDataMerge(
    target: Work.Visible[Identified],
    sources: Seq[Work[Identified]]
  ): ImagesAndImageData = {
    val imagesResult =
      ImagesRule.merge(target, sources.toList).data
    val imageDataResult =
      ImageDataRule
        .merge(target, sources.toList)
        .data
    ImagesAndImageData(imagesResult, imageDataResult)
  }
  describe("image creation rules") {
    it("creates n images from n Miro works and a single Sierra work") {
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
        val metsWork = createInvisibleMetsIdentifiedWorkWith(numImages = 1)
        val miroWork = miroIdentifiedWork()
        val sierraDigmiroWork = sierraIdentifiedWork()
          .format(Format.Pictures)
          .otherIdentifiers(List(createDigcodeIdentifier("digmiro")))
        val result =
          ImagesRule.merge(sierraDigmiroWork, List(miroWork, metsWork)).data

        result should contain theSameElementsAs metsWork.data.imageData
      }

      it(
        "does not use Miro images when multiple METS images are present for a digmiro Sierra work"
      ) {
        val metsWork = createInvisibleMetsIdentifiedWorkWith(numImages = 4)
        val miroWork = miroIdentifiedWork()
        val sierraDigmiroWork = sierraIdentifiedWork()
          .format(Format.Books)
          .otherIdentifiers(List(createDigcodeIdentifier("digmiro")))
        val result =
          imagesAndImageDataMerge(sierraDigmiroWork, List(miroWork, metsWork))
        result.imageData shouldBe empty
      }

      it(
        "does not use Miro images when multiple METS and Miro images are present for a digmiro Sierra work"
      ) {
        val metsWork = createInvisibleMetsIdentifiedWorkWith(numImages = 4)
        val miroWork = miroIdentifiedWork()
        val miroWork2 = miroIdentifiedWork()
        val miroWork3 = miroIdentifiedWork()
        val sierraDigmiroWork = sierraIdentifiedWork()
          .format(Format.Pictures)
          .otherIdentifiers(List(createDigcodeIdentifier("digmiro")))
        val result =
          ImagesRule
            .merge(
              sierraDigmiroWork,
              List(miroWork, miroWork2, miroWork3, metsWork)
            )
            .data

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
  describe("when a Sierra work is digmiro") {
    val sierraDigmiroWork = sierraIdentifiedWork().otherIdentifiers(
      List(createDigcodeIdentifier("digmiro"))
    )
    forAll(Table("format", Format.values.toList: _*)) {
      format =>
        describe(s"and the Sierra work has the format $format") {
          val sierraDigmiroWorkWithFormat =
            sierraDigmiroWork
              .format(format)
              .items(
                List(
                  createIdentifiedPhysicalItem,
                  createIdentifiedPhysicalItem,
                  createUnidentifiableItemWith(locations =
                    List(createDigitalLocation)
                  ),
                  createUnidentifiableItemWith(locations =
                    List(createDigitalLocation)
                  )
                )
              )
          describe(s"when it is merged with a miro work only") {
            val miroWork = miroIdentifiedWork()
            val result =
              imagesAndImageDataMerge(
                sierraDigmiroWorkWithFormat,
                List(miroWork)
              )
            it(
              "does not use Miro images in ImageData and no images are emitted"
            ) {
              result.imageData shouldBe empty
              result.images shouldBe empty
            }
          }
          describe(s"when it is merged with a mets and a miro work") {
            val miroWork1 = miroIdentifiedWork()
            val miroWork2 = miroIdentifiedWork()
            val metsWork = createInvisibleMetsIdentifiedWorkWith(numImages = 1)
            val result =
              imagesAndImageDataMerge(
                sierraDigmiroWorkWithFormat,
                List(miroWork1, metsWork, miroWork2)
              )
            it(
              "does not use Miro images in ImageData"
            ) {
              result.imageData shouldBe empty
            }

            val (prefix, expected) = format match {
              case _: Format.Pictures.type | _: Format.Ephemera.type =>
                ("emits", metsWork.data.imageData)
              case _ =>
                ("does not emit", Nil)
            }
            it(
              s"$prefix the METS images"
            ) {
              //  Only Pictures and Ephemera
              //  emit METS images.  This is because other formats (e.g. Books) have
              //  squillions of images and it would make the image search horrible.
              result.images should contain theSameElementsAs expected
            }
          }
        }
    }
  }
}
