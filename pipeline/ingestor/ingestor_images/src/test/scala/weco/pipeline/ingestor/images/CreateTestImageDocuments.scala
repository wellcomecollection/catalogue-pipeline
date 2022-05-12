package weco.pipeline.ingestor.images

import io.circe.Json
import io.circe.syntax._
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.generators.ImageGenerators
import weco.catalogue.internal_model.image.{Image, ImageState}
import weco.catalogue.internal_model.Implicits._
import weco.catalogue.internal_model.locations.License
import weco.catalogue.internal_model.work.generators.GenreGenerators
import weco.catalogue.internal_model.work.{Agent, Contributor, Meeting, Organisation, Person}
import weco.json.JsonUtil._
import weco.pipeline.ingestor.fixtures.TestDocumentUtils

/** Creates the example documents we use in the API tests.
 *
 * These tests use a seeded RNG to ensure deterministic results; to prevent
 * regenerating existing examples and causing unnecessary churn in the API tests
 * when values change, I suggest adding new examples at the bottom of this file.
 *
 * Also, be careful removing or editing existing examples.  It may be easier to
 * add a new example than remove an old one, to prevent regenerating some of the
 * examples you aren't editing.
 */
class CreateTestImageDocuments
  extends AnyFunSpec
    with Matchers
    with TestDocumentUtils
    with GenreGenerators
    with ImageGenerators {

  it("creates images with different licenses") {
    val ccByImages = (1 to 5).map { _ =>
      createLicensedImage(License.CCBY)
    }

    val pdmImages = (1 to 2).map { _ =>
      createLicensedImage(License.PDM)
    }

    val images = ccByImages ++ pdmImages

    saveImages(
      images,
      description = "images with different licenses",
      id = "images.different-licenses"
    )
  }

  it("creates images with different contributors") {
    val carrots = Agent("carrots")
    val parrots = Organisation("parrots")
    val parrotsMeeting = Meeting("parrots")
    val rats = Person("rats")

    val images = List(
      createImageData.toAugmentedImageWith(
        parentWork = identifiedWork()
          .contributors(List(carrots).map(Contributor(_, roles = Nil)))
      ),
      createImageData.toAugmentedImageWith(
        parentWork = identifiedWork().contributors(
          List(carrots, parrots).map(Contributor(_, roles = Nil))
        ),
        redirectedWork = Some(
          identifiedWork().contributors(
            List(parrots, parrotsMeeting).map(Contributor(_, roles = Nil))
          )
        )
      ),
      createImageData.toAugmentedImageWith(
        parentWork = identifiedWork().contributors(
          List(carrots, parrotsMeeting).map(Contributor(_, roles = Nil))
        ),
        redirectedWork = Some(
          identifiedWork()
            .contributors(List(rats).map(Contributor(_, roles = Nil)))
        )
      )
    )

    saveImages(
      images,
      description = "images with different contributors",
      id = "images.contributors"
    )
  }

  it("creates images with different genres") {
    val carrotCounselling =
      createGenreWith("Carrot counselling", concepts = Nil)
    val dodoDivination = createGenreWith("Dodo divination", concepts = Nil)
    val emuEntrepreneurship =
      createGenreWith("Emu entrepreneurship", concepts = Nil)
    val falconFinances = createGenreWith("Falcon finances", concepts = Nil)

    val carrotCounsellingImage = createImageData.toAugmentedImageWith(
      parentWork = identifiedWork().genres(List(carrotCounselling))
    )
    val redirectedDodoDivinationImage = createImageData.toAugmentedImageWith(
      redirectedWork = Some(identifiedWork().genres(List(dodoDivination)))
    )
    val carrotEmuFalconImage =
      createImageData.toAugmentedImageWith(
        parentWork = identifiedWork().genres(
          List(emuEntrepreneurship, falconFinances, carrotCounselling)
        )
      )

    val images = List(
      carrotCounsellingImage,
      redirectedDodoDivinationImage,
      carrotEmuFalconImage
    )

    saveImages(
      images,
      description = "images with different genres",
      id = "images.genres"
    )
  }

  it("creates images with similar features and palettes") {
    val images =
      createSimilarImages(
        n = 6,
        similarFeatures = true,
        similarPalette = true
      )

    saveImages(
      images,
      description = "images with similar features and palettes",
      id = "images.similar-features-and-palettes"
    )
  }

  it("creates images with similar features") {
    val images =
      createSimilarImages(
        n = 6,
        similarFeatures = true,
        similarPalette = false
      )

    saveImages(
      images,
      description = "images with similar features",
      id = "images.similar-features"
    )
  }

  it("creates images with similar palettes") {
    val images =
      createSimilarImages(
        n = 6,
        similarFeatures = false,
        similarPalette = true
      )

    saveImages(
      images,
      description = "images with similar palettes",
      id = "images.similar-palettes"
    )
  }

  it("creates an image without any inferred data") {
    val image = createImageData.toAugmentedImageWith(inferredData = None)

    saveImage(
      image,
      description = "an image without any inferred data",
      id = "images.inferred-data.none"
    )
  }

  it("creates an image with inferred data in the wrong format") {
    val image = createImageData.toAugmentedImageWith(
      inferredData = createInferredData.map(
        _.copy(
          binMinima = List(1f),
          binSizes = List(List(1))
        )
      )
    )

    saveImage(
      image,
      description = "an image with inferred data in the wrong format",
      id = "images.inferred-data.wrong-format"
    )
  }

  it("creates examples for the contributor filter tests") {
    val machiavelli =
      Contributor(agent = Person("Machiavelli, Niccolo"), roles = Nil)
    val hypatia = Contributor(agent = Person("Hypatia"), roles = Nil)
    val said = Contributor(agent = Person("Edward Said"), roles = Nil)

    val canonicalMachiavelliImage = createImageData.toAugmentedImageWith(
      parentWork = identifiedWork().contributors(List(machiavelli))
    )
    val canonicalSaidImage = createImageData.toAugmentedImageWith(
      parentWork = identifiedWork().contributors(List(said))
    )
    val redirectedHypatiaImage = createImageData.toAugmentedImageWith(
      redirectedWork = Some(identifiedWork().contributors(List(hypatia)))
    )

    val images = List(
      canonicalMachiavelliImage,
      canonicalSaidImage,
      redirectedHypatiaImage
    )

    saveImages(
      images,
      description = "examples for the contributor filter tests",
      id = "images.examples.contributor-filter-tests"
    )
  }

  it("creates examples for the genre filter tests") {
    val carrotCounselling = createGenreWith("Carrot counselling")
    val dodoDivination = createGenreWith("Dodo divination")
    val emuEntrepreneurship = createGenreWith("Emu entrepreneurship")
    val falconFinances = createGenreWith("Falcon finances")

    val carrotCounsellingImage = createImageData.toAugmentedImageWith(
      parentWork = identifiedWork().genres(List(carrotCounselling))
    )
    val redirectedDodoDivinationImage = createImageData.toAugmentedImageWith(
      redirectedWork = Some(identifiedWork().genres(List(dodoDivination)))
    )
    val emuEntrepreneurShipAndFalconFinancesImage =
      createImageData.toAugmentedImageWith(
        parentWork =
          identifiedWork().genres(List(emuEntrepreneurship, falconFinances))
      )

    val images = List(
      carrotCounsellingImage,
      redirectedDodoDivinationImage,
      emuEntrepreneurShipAndFalconFinancesImage
    )

    saveImages(
      images,
      description = "examples for the genre filter tests",
      id = "images.examples.genre-filter-tests"
    )
  }

  it("creates examples for the color filter tests") {
    val redImage = createImageData.toAugmentedImageWith(
      inferredData = createInferredData.map(
        _.copy(
          palette = List(
            "7/0",
            "7/0",
            "7/0",
            "71/1",
            "71/1",
            "71/1",
            "268/2",
            "268/2",
            "268/2"
          )
        )
      )
    )
    val blueImage = createImageData.toAugmentedImageWith(
      inferredData = createInferredData.map(
        _.copy(
          palette = List(
            "9/0",
            "9/0",
            "9/0",
            "5/0",
            "74/1",
            "74/1",
            "74/1",
            "35/1",
            "50/1",
            "29/1",
            "38/1",
            "273/2",
            "273/2",
            "273/2",
            "187/2",
            "165/2",
            "115/2",
            "129/2"
          )
        )
      )
    )
    val slightlyLessRedImage = createImageData.toAugmentedImageWith(
      inferredData = createInferredData.map(
        _.copy(
          palette = List(
            "7/0",
            "71/1",
            "71/1",
            "71/1"
          )
        )
      )
    )
    val evenLessRedImage = createImageData.toAugmentedImageWith(
      inferredData = createInferredData.map(
        _.copy(
          palette = List(
            "7/0",
            "7/0",
            "7/0"
          )
        )
      )
    )

    saveImage(
      redImage,
      description = "example for the color filter tests",
      id = "images.examples.color-filter-tests.red"
    )

    saveImage(
      slightlyLessRedImage,
      description = "example for the color filter tests",
      id = "images.examples.color-filter-tests.slightly-less-red"
    )

    saveImage(
      evenLessRedImage,
      description = "example for the color filter tests",
      id = "images.examples.color-filter-tests.even-less-red"
    )

    saveImage(
      blueImage,
      description = "example for the color filter tests",
      id = "images.examples.color-filter-tests.blue"
    )
  }

  def saveImage(
    image: Image[ImageState.Augmented],
    description: String,
    id: String
  ): Unit =
    saveImages(images = List(image), description, id)

  private def saveImages(
    images: Seq[Image[ImageState.Augmented]],
    description: String,
    id: String
  ): Unit = {

    val documents = images match {
      case Seq(image) =>
        Seq(
          id -> TestDocument(
            description,
            id = image.id,
            document = image.toDocument,
            image = image
          )
        )

      case _ =>
        images.zipWithIndex
          .map {
            case (image, index) =>
              s"$id.$index" -> TestDocument(
                description,
                id = image.id,
                document = image.toDocument,
                image = image
              )
          }
    }

    saveDocuments(documents)
  }

  implicit class ImageOps(image: Image[ImageState.Augmented]) {
    def toDocument: Json =
      ImageTransformer.deriveData(image).asJson
  }
}
