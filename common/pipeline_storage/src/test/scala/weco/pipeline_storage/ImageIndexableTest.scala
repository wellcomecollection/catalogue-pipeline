package weco.pipeline_storage

import com.sksamuel.elastic4s.Index
import org.scalatest.Assertion
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.index.ImagesIndexConfig
import weco.catalogue.internal_model.fixtures.index.IndexFixtures
import weco.fixtures.TestWith
import weco.catalogue.internal_model.Implicits._
import Indexable.imageIndexable
import weco.catalogue.internal_model.generators.ImageGenerators
import weco.catalogue.internal_model.image.{Image, ImageState}
import weco.catalogue.internal_model.matchers.EventuallyInElasticsearch
import weco.catalogue.internal_model.work.generators.WorkGenerators
import weco.pipeline_storage.elastic.ElasticIndexer
import weco.pipeline_storage.fixtures.ElasticIndexerFixtures

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class ImageIndexableTest
    extends AnyFunSpec
    with Matchers
    with IndexFixtures
    with ElasticIndexerFixtures
    with EventuallyInElasticsearch
    with ImageGenerators
    with WorkGenerators {

  describe("updating images with merged / redirected sources") {
    it("overrides an image if modifiedTime is higher") {
      val originalImage = createImageData.toAugmentedImage
      val updatedModifiedTimeImage = originalImage.copy(
        modifiedTime = originalImage.modifiedTime + (2 minutes)
      )

      withImagesIndexAndIndexer {
        case (index, indexer) =>
          val insertFuture = indexInOrder(indexer)(
            originalImage,
            updatedModifiedTimeImage
          )

          whenReady(insertFuture) {
            result =>
              assertIngestedImageIs(
                result = result,
                image = updatedModifiedTimeImage,
                index = index
              )
          }
      }
    }
    it("does not override an image if modifiedTime is lower") {
      val originalImage = createImageData.toAugmentedImage
      val updatedModifiedTimeImage = originalImage.copy(
        modifiedTime = originalImage.modifiedTime - (2 minutes)
      )

      withImagesIndexAndIndexer {
        case (index, indexer) =>
          val insertFuture = indexInOrder(indexer)(
            originalImage,
            updatedModifiedTimeImage
          )

          whenReady(insertFuture) {
            result =>
              assertIngestedImageIs(
                result = result,
                image = originalImage,
                index = index
              )
          }
      }
    }
    it("overrides an image if modifiedTime is the same") {
      val originalImage = createImageData.toAugmentedImage
      val updatedLocationImage = originalImage.copy(
        locations = originalImage.locations.tail
      )

      withImagesIndexAndIndexer {
        case (index, indexer) =>
          val insertFuture = indexInOrder(indexer)(
            originalImage,
            updatedLocationImage
          )

          whenReady(insertFuture) {
            result =>
              assertIngestedImageIs(
                result = result,
                image = updatedLocationImage,
                index = index
              )
          }
      }
    }
  }

  private def assertIngestedImageIs(
    result: Either[Seq[Image[ImageState.Augmented]], Seq[
      Image[ImageState.Augmented]
    ]],
    image: Image[ImageState.Augmented],
    index: Index
  ): Seq[Assertion] = {
    result shouldBe a[Right[_, _]]
    assertElasticsearchEventuallyHasImage(index, image)
  }

  private def withImagesIndexAndIndexer[R](
    testWith: TestWith[(Index, ElasticIndexer[Image[ImageState.Augmented]]), R]
  ) =
    withLocalAugmentedImageIndex {
      index =>
        val indexer = new ElasticIndexer[Image[ImageState.Augmented]](
          elasticClient,
          index,
          ImagesIndexConfig.augmented
        )
        testWith((index, indexer))
    }
}
