package uk.ac.wellcome.platform.api.services

import com.sksamuel.elastic4s.{ElasticError, Index}
import org.scalatest.funspec.AsyncFunSpec
import org.scalatest.{EitherValues, OptionValues}
import uk.ac.wellcome.elasticsearch.test.fixtures.ElasticsearchFixtures
import uk.ac.wellcome.models.work.generators.ImageGenerators

class ImagesServiceTest
    extends AsyncFunSpec
    with ElasticsearchFixtures
    with ImageGenerators
    with EitherValues
    with OptionValues {

  val elasticsearchService = new ElasticsearchService(
    elasticClient = elasticClient,
    ImagesRequestBuilder
  )

  val imagesService = new ImagesService(
    elasticsearchService
  )

  describe("findImageById") {
    it("fetches an Image by ID") {
      withLocalImagesIndex { index =>
        val image = createAugmentedImage()
        insertImagesIntoElasticsearch(index, image)

        imagesService
          .findImageById(id = image.id.canonicalId)(index)
          .map { _.right.value.value shouldBe image }
      }
    }

    it("returns a None if no image can be found") {
      withLocalImagesIndex { index =>
        imagesService
          .findImageById("bananas")(index)
          .map { _.right.value shouldBe None }
      }
    }

    it("returns a Left[ElasticError] if Elasticsearch returns an error") {
      imagesService
        .findImageById("potatoes")(Index("parsnips"))
        .map { _.left.value shouldBe a[ElasticError] }
    }
  }

  describe("retrieveSimilarImages") {
    it("gets visually similarImages") {
      withLocalImagesIndex { index =>
        val images = createVisuallySimilarImages(6)
        insertImagesIntoElasticsearch(index, images: _*)

        imagesService
          .retrieveSimilarImages(index, images.head)
          .map { results =>
            results should not be empty
            results should contain theSameElementsAs images.tail
          }
      }
    }

    it("returns Nil when no visually similar images can be found") {
      withLocalImagesIndex { index =>
        val image = createAugmentedImage()
        insertImagesIntoElasticsearch(index, image)

        imagesService.retrieveSimilarImages(index, image).map { results =>
          results shouldBe empty
        }
      }
    }

    it("returns Nil when Elasticsearch returns an error") {
      imagesService
        .retrieveSimilarImages(Index("doesn't exist"), createAugmentedImage())
        .map { results =>
          results shouldBe empty
        }
    }
  }
}
