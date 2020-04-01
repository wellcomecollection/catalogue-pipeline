package uk.ac.wellcome.elasticsearch

import com.sksamuel.elastic4s.ElasticError
import org.scalatest.FunSpec
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import uk.ac.wellcome.elasticsearch.test.fixtures.ElasticsearchFixtures
import uk.ac.wellcome.models.work.generators.ImageGenerators
//import uk.ac.wellcome.models.work.internal.{AugmentedImage, Minted}
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.json.JsonUtil._
//import org.scalacheck.ScalacheckShapeless._

class ImagesIndexConfigTest extends FunSpec with ImageGenerators with ElasticsearchFixtures with ScalaFutures with ScalaCheckPropertyChecks{
  it("can ingest an image") {
    withLocalImagesIndex { index =>
//    forAll { image: AugmentedImage[Minted] =>
        val image = createMergedImage.toMinted.augment(None)
        whenReady(indexObject(index, image)) { response =>
          response.isError shouldBe false
          assertObjectIndexed(index, image)
        }
      }
//    }
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
