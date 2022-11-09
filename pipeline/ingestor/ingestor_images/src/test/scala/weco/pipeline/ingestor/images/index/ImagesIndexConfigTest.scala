package weco.pipeline.ingestor.images.index

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.{ElasticError, Index}
import org.scalatest.Assertion
import org.scalatest.funspec.AnyFunSpec
import weco.catalogue.internal_model.Implicits._
import weco.catalogue.internal_model.generators.ImageGenerators
import weco.catalogue.internal_model.image.{Image, ImageState, InferredData}
import weco.catalogue.internal_model.index.IndexFixtures
import weco.json.JsonUtil._
import weco.pipeline.ingestor.images.ImageTransformer
import weco.pipeline.ingestor.images.models.IndexedImage

import scala.util.Random

class ImagesIndexConfigTest
    extends AnyFunSpec
    with ImageGenerators
    with IndexFixtures {

  it("indexes an image with large image features vectors") {
    withLocalImagesIndex { implicit index =>
      assertImageCanBeIndexed(
        image = createImageData.toAugmentedImage
      )
    }
  }

  it("cannot index an image with image vectors that are too long") {
    withLocalImagesIndex { implicit index =>
      val features1 = (0 until 3000).map(_ => Random.nextFloat() * 100).toList
      val features2 = (0 until 3000).map(_ => Random.nextFloat() * 100).toList
      val reducedFeatures =
        (0 until 3000).map(_ => Random.nextFloat() * 100).toList
      val image = createImageData.toAugmentedImageWith(
        inferredData = InferredData(
          features1,
          features2,
          reducedFeatures,
          List(randomAlphanumeric(10)),
          Some(randomHexString),
          List(List(4, 6, 9), List(2, 4, 6), List(1, 3, 5)),
          List(0f, 10f / 256, 10f / 256),
          Some(Random.nextFloat())
        )
      )

      val response = indexImage(id = image.id, image = image)
      response.isError shouldBe true
      response.error shouldBe a[ElasticError]
    }
  }

  it("cannot index an image with image vectors that are too short") {
    withLocalImagesIndex { implicit index =>
      val image = createImageData.toAugmentedImageWith(
        inferredData = InferredData(
          List(2.0f),
          List(2.0f),
          List(2.0f),
          List(randomAlphanumeric(10)),
          Some(randomHexString),
          List(List(4, 6, 9), List(2, 4, 6), List(1, 3, 5)),
          List(0f, 10f / 256, 10f / 256),
          Some(Random.nextFloat())
        )
      )

      val response = indexImage(id = image.id, image = image)
      response.isError shouldBe true
      response.error shouldBe a[ElasticError]
    }
  }

  private def assertImageCanBeIndexed(image: Image[ImageState.Augmented])(
    implicit index: Index
  ): Assertion = {
    val r = indexImage(id = image.id, image = image)

    if (r.isError) {
      println(s"Could not index image: $r")
    }

    assertImageIsIndexed(id = image.id, image = image)
  }

  private def indexImage(
    id: String,
    image: Image[ImageState.Augmented]
  )(implicit index: Index) =
    elasticClient.execute {
      indexInto(index)
        .doc(toJson(ImageTransformer.deriveData(image)).get)
        .id(id)
    }.await

  private def assertImageIsIndexed(
    id: String,
    image: Image[ImageState.Augmented]
  )(implicit index: Index) =
    eventually {
      whenReady(elasticClient.execute(get(index, id))) { getResponse =>
        getResponse.result.exists shouldBe true

        fromJson[IndexedImage](getResponse.result.sourceAsString).get shouldBe ImageTransformer
          .deriveData(image)
      }
    }
}
