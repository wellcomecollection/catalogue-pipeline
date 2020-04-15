package uk.ac.wellcome.elasticsearch

import com.sksamuel.elastic4s.ElasticError
import org.scalatest.FunSpec
import org.scalatest.concurrent.ScalaFutures
import uk.ac.wellcome.elasticsearch.test.fixtures.ElasticsearchFixtures
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.models.work.generators.ImageGenerators
import uk.ac.wellcome.models.work.internal.InferredData

import scala.util.Random

class ImagesIndexConfigTest
    extends FunSpec
    with ImageGenerators
    with ElasticsearchFixtures
    with ScalaFutures {

  it("can ingest an image with large image features vectors") {
    withLocalImagesIndex { index =>
      val image = createAugmentedImage()
      whenReady(indexObject(index, image)) { response =>
        response.isError shouldBe false
        assertObjectIndexed(index, image)
      }

    }
  }

  it("can ingest an image without feature vectors") {
    withLocalImagesIndex { index =>
      val image = createAugmentedImage(inferredData = None)
      whenReady(indexObject(index, image)) { response =>
        response.isError shouldBe false
        assertObjectIndexed(index, image)
      }

    }
  }

  it("cannot ingest an image with image vectors that are shorter than 2048") {
    withLocalImagesIndex { index =>
      val features1 = (0 until 3000).map(_ => Random.nextFloat() * 100).toList
      val features2 = (0 until 3000).map(_ => Random.nextFloat() * 100).toList
      val image = createMergedImage.toMinted.augment(
        Some(InferredData(features1, features2, List(randomAlphanumeric(10)))))
      whenReady(indexObject(index, image)) { response =>
        response.isError shouldBe true
        response.error shouldBe a[ElasticError]
      }
    }
  }

  it("cannot ingest an image with image vectors that are longer than 2048") {
    withLocalImagesIndex { index =>
      val image = createMergedImage.toMinted.augment(
        Some(
          InferredData(List(2.0f), List(2.0f), List(randomAlphanumeric(10)))))
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
