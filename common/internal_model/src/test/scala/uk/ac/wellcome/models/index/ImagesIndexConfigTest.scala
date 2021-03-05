package uk.ac.wellcome.models.index

import com.sksamuel.elastic4s.ElasticError
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.funspec.AnyFunSpec
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.models.work.generators.ImageGenerators
import uk.ac.wellcome.models.work.internal.InferredData

import scala.util.Random

class ImagesIndexConfigTest
    extends AnyFunSpec
    with ImageGenerators
    with IndexFixtures
    with ScalaFutures {

  it("can ingest an image with large image features vectors") {
    withLocalImagesIndex { index =>
      val image = createImageData.toAugmentedImage
      whenReady(indexObject(index, image)) { response =>
        response.isError shouldBe false
        assertObjectIndexed(index, image)
      }

    }
  }

  it("can ingest an image without feature vectors") {
    withLocalImagesIndex { index =>
      val image = createImageData.toAugmentedImageWith(inferredData = None)
      whenReady(indexObject(index, image)) { response =>
        response.isError shouldBe false
        assertObjectIndexed(index, image)
      }

    }
  }

  it("cannot ingest an image with image vectors that are longer than 2048") {
    withLocalImagesIndex { index =>
      val features1 = (0 until 3000).map(_ => Random.nextFloat() * 100).toList
      val features2 = (0 until 3000).map(_ => Random.nextFloat() * 100).toList
      val image = createImageData.toAugmentedImageWith(
        inferredData = Some(
          InferredData(
            features1,
            features2,
            List(randomAlphanumeric(10)),
            List(randomAlphanumeric(10)),
            List(List(4, 6, 9), List(2, 4, 6), List(1, 3, 5)),
            List(0f, 10f / 256, 10f / 256))))
      whenReady(indexObject(index, image)) { response =>
        response.isError shouldBe true
        response.error shouldBe a[ElasticError]
      }
    }
  }

  it("cannot ingest an image with image vectors that are shorter than 2048") {
    withLocalImagesIndex { index =>
      val image = createImageData.toAugmentedImageWith(
        inferredData = Some(
          InferredData(
            List(2.0f),
            List(2.0f),
            List(randomAlphanumeric(10)),
            List(randomAlphanumeric(10)),
            List(List(4, 6, 9), List(2, 4, 6), List(1, 3, 5)),
            List(0f, 10f / 256, 10f / 256))))
      whenReady(indexObject(index, image)) { response =>
        response.isError shouldBe true
        response.error shouldBe a[ElasticError]
      }
    }
  }

  it("doesn't ingest something that it's not an image") {
    case class BadDocument(Something: String, somethingElse: Int)
    val document = BadDocument(randomAlphanumeric(10), 10)
    withLocalImagesIndex { index =>
      whenReady(indexObject(index, document)) { response =>
        response.isError shouldBe true
        response.error shouldBe a[ElasticError]
      }
    }
  }
}
