package uk.ac.wellcome.pipeline_storage

import com.sksamuel.elastic4s.Index
import org.scalatest.Assertion
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.elasticsearch.test.fixtures.ElasticsearchFixtures
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.models.work.generators.{ImageGenerators, WorkGenerators}
import uk.ac.wellcome.models.work.internal.{
  AugmentedImage,
  DataState,
  SourceWorks
}
import uk.ac.wellcome.models.work.internal.SourceWork._
import uk.ac.wellcome.pipeline_storage.Indexable.imageIndexable
import uk.ac.wellcome.pipeline_storage.fixtures.ElasticIndexerFixtures

import scala.concurrent.ExecutionContext.Implicits.global

class ImageIndexableTest
    extends AnyFunSpec
    with ScalaFutures
    with Matchers
    with ElasticsearchFixtures
    with ElasticIndexerFixtures
    with ImageGenerators
    with WorkGenerators {

  describe("updating images with merged / redirected sources") {
    it("overrides an image with the same version if source version is higher") {
      val source = identifiedWork()
      val originalImage = createAugmentedImageWith(
        parentWork = source,
        redirectedWork = None
      )
      val updatedSourceImage = originalImage.copy(
        source =
          SourceWorks(
            canonicalWork =
              source.toSourceWork.copy(version = source.version + 1),
            redirectedWork = None
          )
      )

      withImagesIndexAndIndexer {
        case (index, indexer) =>
          val insertFuture = ingestInOrder(indexer)(
            originalImage,
            updatedSourceImage
          )

          whenReady(insertFuture) { result =>
            assertIngestedImageIs(
              result = result,
              ingestedImage = updatedSourceImage,
              index = index
            )
          }
      }
    }

    it("overrides an image with the same version if sources have been merged") {
      val source = identifiedWork()
      val originalImage = createAugmentedImageWith(
        parentWork = source,
        redirectedWork = None,
      )
      val mergedSourceImage = originalImage.copy(
        source = originalImage.source
          .asInstanceOf[SourceWorks[DataState.Identified]]
          .copy(
            redirectedWork = Some(source.toSourceWork)
          )
      )

      withImagesIndexAndIndexer {
        case (index, indexer) =>
          val insertFuture = ingestInOrder(indexer)(
            originalImage,
            mergedSourceImage
          )

          whenReady(insertFuture) { result =>
            assertIngestedImageIs(
              result = result,
              ingestedImage = mergedSourceImage,
              index = index
            )
          }
      }
    }

    it(
      "doesn't override an image with a lower version and the same source versions") {
      val originalImage = createAugmentedImageWith(version = 3)
      val lowerVersionImage = originalImage.copy(version = 2)

      withImagesIndexAndIndexer {
        case (index, indexer) =>
          val insertFuture = ingestInOrder(indexer)(
            originalImage,
            lowerVersionImage
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

    it(
      "doesn't override an image with a redirected source with a non-redirected one") {
      val originalImage = createAugmentedImage
      val nonRedirectedImage = originalImage.copy(
        source = originalImage.source
          .asInstanceOf[SourceWorks[DataState.Identified]]
          .copy(
            redirectedWork = None
          )
      )

      withImagesIndexAndIndexer {
        case (index, indexer) =>
          val insertFuture = ingestInOrder(indexer)(
            originalImage,
            nonRedirectedImage
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

    it("throws an error if an image has >= versionMultiplier merged sources") {
      val erroneousImage = createAugmentedImage().copy(
        source = SourceWorks(
          canonicalWork = identifiedWork().toSourceWork,
          redirectedWork = None,
          numberOfSources = Indexable.versionMultiplier
        )
      )

      withImagesIndexAndIndexer {
        case (_, indexer) =>
          a[RuntimeException] should be thrownBy {
            ingestInOrder(indexer)(erroneousImage)
          }
      }
    }
  }

  private def assertIngestedImageIs(
    result: Either[Seq[AugmentedImage], Seq[AugmentedImage]],
    ingestedImage: AugmentedImage,
    index: Index): Seq[Assertion] = {
    result.isRight shouldBe true
    assertElasticsearchEventuallyHasImage(index, ingestedImage)
  }

  private def withImagesIndexAndIndexer[R](
    testWith: TestWith[(Index, ElasticIndexer[AugmentedImage]), R]) =
    withLocalImagesIndex { index =>
      val indexer = new ElasticIndexer(elasticClient, index)
      testWith((index, indexer))
    }
}
