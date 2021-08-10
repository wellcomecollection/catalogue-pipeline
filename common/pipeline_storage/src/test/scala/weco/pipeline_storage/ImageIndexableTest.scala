package weco.pipeline_storage

import com.sksamuel.elastic4s.Index
import org.scalatest.Assertion
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.index.{ImagesIndexConfig, IndexFixtures}
import weco.fixtures.TestWith
import weco.catalogue.internal_model.Implicits._
import weco.catalogue.internal_model.work.generators.WorkGenerators
import Indexable.imageIndexable
import weco.catalogue.internal_model.generators.ImageGenerators
import weco.catalogue.internal_model.image.{Image, ImageState}
import weco.catalogue.internal_model.work.generators.{
  InstantGenerators,
  WorkGenerators
}
import weco.pipeline_storage.elastic.ElasticIndexer
import weco.pipeline_storage.fixtures.ElasticIndexerFixtures

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class ImageIndexableTest
    extends AnyFunSpec
    with ScalaFutures
    with Matchers
    with IndexFixtures
    with ElasticIndexerFixtures
    with ImageGenerators
    with WorkGenerators
    with InstantGenerators {

  describe("updating images with merged / redirected sources") {
    it("overrides an image if modifiedTime is higher") {
      val originalImage = createImageData.toIndexedImage
      val updatedModifiedTimeImage = originalImage.copy(
        modifiedTime = originalImage.modifiedTime + (2 minutes)
      )

      withImagesIndexAndIndexer {
        case (index, indexer) =>
          val insertFuture = indexInOrder(indexer)(
            originalImage,
            updatedModifiedTimeImage
          )

          whenReady(insertFuture) { result =>
            assertIngestedImageIs(
              result = result,
              ingestedImage = updatedModifiedTimeImage,
              index = index
            )
          }
      }
    }
    it("does not override an image if modifiedTime is lower") {
      val originalImage = createImageData.toIndexedImage
      val updatedModifiedTimeImage = originalImage.copy(
        modifiedTime = originalImage.modifiedTime - (2 minutes)
      )

      withImagesIndexAndIndexer {
        case (index, indexer) =>
          val insertFuture = indexInOrder(indexer)(
            originalImage,
            updatedModifiedTimeImage
          )

          whenReady(insertFuture) { result =>
            assertIngestedImageIs(
              result = result,
              ingestedImage = originalImage,
              index = index
            )
          }
      }
    }
    it("overrides an image if modifiedTime is the same") {
      val originalImage = createImageData.toIndexedImage
      val updatedLocationImage = originalImage.copy(
        locations = originalImage.locations.tail
      )

      withImagesIndexAndIndexer {
        case (index, indexer) =>
          val insertFuture = indexInOrder(indexer)(
            originalImage,
            updatedLocationImage
          )

          whenReady(insertFuture) { result =>
            assertIngestedImageIs(
              result = result,
              ingestedImage = updatedLocationImage,
              index = index
            )
          }
      }
    }
  }

  private def assertIngestedImageIs(
    result: Either[Seq[Image[ImageState.Indexed]],
                   Seq[Image[ImageState.Indexed]]],
    ingestedImage: Image[ImageState.Indexed],
    index: Index): Seq[Assertion] = {
    result.isRight shouldBe true
    assertElasticsearchEventuallyHasImage(index, ingestedImage)
  }

  private def withImagesIndexAndIndexer[R](
    testWith: TestWith[(Index, ElasticIndexer[Image[ImageState.Indexed]]), R]) =
    withLocalImagesIndex { index =>
      val indexer = new ElasticIndexer[Image[ImageState.Indexed]](
        elasticClient,
        index,
        ImagesIndexConfig.ingested)
      testWith((index, indexer))
    }
}
