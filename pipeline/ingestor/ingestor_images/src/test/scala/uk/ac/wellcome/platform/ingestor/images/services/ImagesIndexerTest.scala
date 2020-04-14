package uk.ac.wellcome.platform.ingestor.images.services

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.elasticsearch.test.fixtures.ElasticsearchFixtures
import uk.ac.wellcome.models.work.generators.ImageGenerators
import uk.ac.wellcome.models.Implicits._
import scala.concurrent.ExecutionContext.Implicits.global

class ImagesIndexerTest extends FunSpec with Matchers with ElasticsearchFixtures with ScalaFutures with ImageGenerators{

  it("ingests an image"){
    withLocalImagesIndex { index =>
      val imagesIndexer = new ImagesIndexer(elasticClient,index)
      implicit val i = imagesIndexer.id
      val image = createAugmentedImage()
      whenReady(imagesIndexer.index(List(image))){ r =>
        r.isRight shouldBe true
        r.right.get shouldBe List(image)
        assertElasticsearchEventuallyHas(index = index, image)
      }
    }
  }

  it("ingests a list of images"){
    withLocalImagesIndex { index =>
      val imagesIndexer = new ImagesIndexer(elasticClient,index)
      implicit val i = imagesIndexer.id
      val images = (1 to 5).map(_ =>createAugmentedImage())
      whenReady(imagesIndexer.index(images)){ r =>
        r.isRight shouldBe true
        r.right.get should contain theSameElementsAs images
        assertElasticsearchEventuallyHas(index = index, images: _*)
      }
    }
  }

  it("ingests a higher version of the same image"){
    withLocalImagesIndex { index =>
      val imagesIndexer = new ImagesIndexer(elasticClient,index)
      implicit val i = imagesIndexer.id
      val image = createAugmentedImage()
      val newerImage = image.copy(version = image.version + 1)
      val result = for {
        _ <- imagesIndexer.index(List(image))
        res <- imagesIndexer.index(List(newerImage))
      } yield res
      whenReady(result){ r =>
        r.isRight shouldBe true
        r.right.get shouldBe List(newerImage)
        assertElasticsearchEventuallyHas(index = index, newerImage)
      }
    }
  }

  it("doesn't replace a newer version with a lower one"){
    withLocalImagesIndex { index =>
      val imagesIndexer = new ImagesIndexer(elasticClient,index)
      implicit val i = imagesIndexer.id
      val image = createAugmentedImage()
      val olderImage = image.copy(version = image.version - 1)
      val result = for {
        _ <- imagesIndexer.index(List(image))
        res <- imagesIndexer.index(List(olderImage))
      } yield res
      whenReady(result){ r =>
        r.isRight shouldBe true
        r.right.get shouldBe List(olderImage)
        assertElasticsearchEventuallyHas(index = index, image)
      }
    }
  }
}
