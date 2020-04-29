package uk.ac.wellcome.platform.api.images

import uk.ac.wellcome.elasticsearch.ElasticConfig
import uk.ac.wellcome.elasticsearch.test.fixtures.ElasticsearchFixtures

class ImagesTest extends ApiImagesTestBase with ElasticsearchFixtures {

  it("returns a single image when requested with ID") {
    withApi {
      case (ElasticConfig(_, imagesIndex), routes) =>
        val image = createAugmentedImage()
        insertImagesIntoElasticsearch(imagesIndex, image)
        assertJsonResponse(
          routes,
          s"/$apiPrefix/images/${image.id.canonicalId}") {
          Status.OK ->
            s"""
             |{
             |  $singleImageResult,
             |  "id": "${image.id.canonicalId}",
             |  "location": ${location(image.location)},
             |  "parentWork": "${image.parentWork.canonicalId}"
             |}""".stripMargin
        }
    }
  }

  it("searches different indices with the _index parameter") {
    withApi {
      case (ElasticConfig(_, defaultIndex), routes) =>
        withLocalImagesIndex { alternativeIndex =>
          val defaultImage = createAugmentedImage()
          val alternativeImage = createAugmentedImage()
          insertImagesIntoElasticsearch(defaultIndex, defaultImage)
          insertImagesIntoElasticsearch(alternativeIndex, alternativeImage)

          assertJsonResponse(
            routes,
            s"/$apiPrefix/images/${defaultImage.id.canonicalId}") {
            Status.OK ->
              s"""
                 |{
                 |  $singleImageResult,
                 |  "id": "${defaultImage.id.canonicalId}",
                 |  "location": ${location(defaultImage.location)},
                 |  "parentWork": "${defaultImage.parentWork.canonicalId}"
                 |}""".stripMargin
          }

          assertJsonResponse(
            routes,
            s"/$apiPrefix/images/${alternativeImage.id.canonicalId}?_index=${alternativeIndex.name}") {
            Status.OK ->
              s"""
                 |{
                 |  $singleImageResult,
                 |  "id": "${alternativeImage.id.canonicalId}",
                 |  "location": ${location(alternativeImage.location)},
                 |  "parentWork": "${alternativeImage.parentWork.canonicalId}"
                 |}""".stripMargin
          }
        }
    }
  }
}
