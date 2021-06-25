package weco.pipeline.ingestor.images.services

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.index.{ImagesIndexConfig, IndexFixtures}
import weco.pipeline_storage.Indexable.imageIndexable
import weco.catalogue.internal_model.Implicits._
import weco.catalogue.internal_model.generators.ImageGenerators
import weco.catalogue.internal_model.image.{Image, ImageState}
import weco.pipeline_storage.elastic.ElasticIndexer
import weco.pipeline_storage.fixtures.ElasticIndexerFixtures

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class ImagesIndexerTest
    extends AnyFunSpec
    with Matchers
    with IndexFixtures
    with ScalaFutures
    with ElasticIndexerFixtures
    with ImageGenerators {

  it("ingests an image") {
    withLocalImagesIndex { index =>
      val imagesIndexer =
        new ElasticIndexer[Image[ImageState.Augmented]](
          elasticClient,
          index,
          ImagesIndexConfig.ingested)
      val image = createImageData.toAugmentedImage
      whenReady(imagesIndexer(List(image))) { r =>
        r.isRight shouldBe true
        r.right.get shouldBe List(image)
        assertElasticsearchEventuallyHas(index = index, image)
      }
    }
  }

  it("ingests a list of images") {
    withLocalImagesIndex { index =>
      val imagesIndexer =
        new ElasticIndexer[Image[ImageState.Augmented]](
          elasticClient,
          index,
          ImagesIndexConfig.ingested)
      val images = (1 to 5).map(_ => createImageData.toAugmentedImage)
      whenReady(imagesIndexer(images)) { r =>
        r.isRight shouldBe true
        r.right.get should contain theSameElementsAs images
        assertElasticsearchEventuallyHas(index = index, images: _*)
      }
    }
  }

  it("ingests a higher version of the same image") {
    withLocalImagesIndex { index =>
      val imagesIndexer =
        new ElasticIndexer[Image[ImageState.Augmented]](
          elasticClient,
          index,
          ImagesIndexConfig.ingested)
      val image = createImageData.toAugmentedImage
      val newerImage =
        image.copy(
          modifiedTime = image.modifiedTime + (2 minutes)
        )
      val result = for {
        _ <- imagesIndexer(List(image))
        res <- imagesIndexer(List(newerImage))
      } yield res
      whenReady(result) { r =>
        r.isRight shouldBe true
        r.right.get shouldBe List(newerImage)
        assertElasticsearchEventuallyHas(index = index, newerImage)
      }
    }
  }

  it("doesn't replace a newer version with a lower one") {
    withLocalImagesIndex { index =>
      val imagesIndexer =
        new ElasticIndexer[Image[ImageState.Augmented]](
          elasticClient,
          index,
          ImagesIndexConfig.ingested)
      val image = createImageData.toAugmentedImage
      val olderImage =
        image.copy(
          modifiedTime = image.modifiedTime - (2 minutes)
        )
      val result = for {
        _ <- imagesIndexer(List(image))
        res <- imagesIndexer(List(olderImage))
      } yield res
      whenReady(result) { r =>
        r.isRight shouldBe true
        r.right.get shouldBe List(olderImage)
        assertElasticsearchEventuallyHas(index = index, image)
      }
    }
  }
}
