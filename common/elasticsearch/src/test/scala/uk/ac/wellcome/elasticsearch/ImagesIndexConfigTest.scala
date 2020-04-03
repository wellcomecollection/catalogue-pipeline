package uk.ac.wellcome.elasticsearch

import com.sksamuel.elastic4s.ElasticError
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.funspec.AnyFunSpec
import uk.ac.wellcome.elasticsearch.test.fixtures.ElasticsearchFixtures
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.models.work.generators.ImageGenerators
import uk.ac.wellcome.models.work.internal.InferredData

import scala.util.Random

class ImagesIndexConfigTest extends AnyFunSpec with ImageGenerators with ElasticsearchFixtures with ScalaFutures {

  it("can ingest an image") {
     withLocalImagesIndex { index =>
        val image = createMergedImage.toMinted.augment(Some(InferredData(List(2.0f), List(2.0f), List(randomAlphanumeric(10)))))
        whenReady(indexObject(index, image)) { response =>
          response.isError shouldBe false
          assertObjectIndexed(index, image)
        }

    }
  }
  it("can ingest an image with large image features vectors") {
     withLocalImagesIndex { index =>
       val features1 = (0 to 2048).map(_ => Random.nextFloat()*100).toList
       val features2 = (0 to 2048).map(_ => Random.nextFloat()*100).toList
        val image = createMergedImage.toMinted.augment(Some(InferredData(features1, features2, List(randomAlphanumeric(10)))))
        whenReady(indexObject(index, image)) { response =>
          response.isError shouldBe false
          assertObjectIndexed(index, image)
        }

    }
  }

  it("can ingest an image with big feature vectors"){

  }
  it("doesn't ingest something that it's not an image"){
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
