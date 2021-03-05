package uk.ac.wellcome.platform.api.models

import scala.concurrent.ExecutionContext.Implicits.global
import com.sksamuel.elastic4s.Index
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.elasticsearch.ElasticClientBuilder
import uk.ac.wellcome.models.work.generators.ImageGenerators
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.models.index.IndexFixtures

class QueryConfigTest
    extends AnyFunSpec
    with Matchers
    with IndexFixtures
    with ImageGenerators {
  describe("fetchFromIndex") {

    it("fetches query config from a given index") {
      withLocalImagesIndex { index =>
        val image = createImageData.toIndexedImage
        val inferredData = image.state.inferredData.get
        insertImagesIntoElasticsearch(index, image)

        val result = QueryConfig.fetchFromIndex(elasticClient, index)
        result.paletteBinSizes shouldBe inferredData.binSizes
        result.paletteBinMinima shouldBe inferredData.binMinima
      }
    }

    it("returns the default config if the index doesn't exist") {
      val result =
        QueryConfig.fetchFromIndex(elasticClient, Index("not-an-index"))
      result.paletteBinSizes shouldBe QueryConfig.defaultPaletteBinSizes
    }

    it("returns the default config if ES can't be connected to") {
      val badClient = ElasticClientBuilder.create(
        hostname = "not-a-host",
        port = 123456,
        protocol = "http",
        username = "not-good",
        password = "very-bad"
      )
      val result =
        QueryConfig.fetchFromIndex(badClient, Index("not-an-index"))
      result.paletteBinSizes shouldBe QueryConfig.defaultPaletteBinSizes
    }

    it("returns the default config if the data is not in the expected format") {
      withLocalImagesIndex { index =>
        val image = createImageData.toIndexedImageWith(
          inferredData = createInferredData.map(
            _.copy(
              binMinima = List(1f),
              binSizes = List(List(1))
            ))
        )
        insertImagesIntoElasticsearch(index, image)

        val result = QueryConfig.fetchFromIndex(elasticClient, index)
        result.paletteBinSizes shouldBe QueryConfig.defaultPaletteBinSizes
      }
    }

    it("returns the default config if the data is not found") {
      withLocalImagesIndex { index =>
        val image = createImageData.toIndexedImageWith(inferredData = None)
        insertImagesIntoElasticsearch(index, image)

        val result = QueryConfig.fetchFromIndex(elasticClient, index)
        result.paletteBinSizes shouldBe QueryConfig.defaultPaletteBinSizes
      }
    }
  }
}
