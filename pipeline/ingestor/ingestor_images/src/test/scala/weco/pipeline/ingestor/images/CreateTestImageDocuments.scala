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
