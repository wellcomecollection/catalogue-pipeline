package uk.ac.wellcome.platform.api.models

import com.sksamuel.elastic4s.Index
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.elasticsearch.ElasticClientBuilder
import uk.ac.wellcome.elasticsearch.test.fixtures.ElasticsearchFixtures
import uk.ac.wellcome.models.work.generators.ImageGenerators

import scala.concurrent.ExecutionContext.Implicits.global

class QueryConfigTest
    extends AnyFunSpec
    with Matchers
    with ElasticsearchFixtures
    with ImageGenerators {
  describe("fetchFromIndex") {

    it("fetches query config from a given index") {
      withLocalImagesIndex { index =>
        val binSizes = Seq(10, 11, 12)
        val image = createAugmentedImageWith(
          inferredData = createInferredData.map(
            _.copy(
              palette = randomColorVector(binSizes = binSizes).toList
            ))
        )
        insertImagesIntoElasticsearch(index, image)

        val result = QueryConfig.fetchFromIndex(elasticClient, index)
        result.paletteBinSizes shouldBe binSizes
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
        val image = createAugmentedImageWith(
          inferredData = createInferredData.map(
            _.copy(
              palette = List("123", "hello", "bad data")
            ))
        )
        insertImagesIntoElasticsearch(index, image)

        val result = QueryConfig.fetchFromIndex(elasticClient, index)
        result.paletteBinSizes shouldBe QueryConfig.defaultPaletteBinSizes
      }
    }
  }
}
