package uk.ac.wellcome.platform.api.images

import uk.ac.wellcome.elasticsearch.ElasticConfig

class ImagesSimilarityTest extends ApiImagesTestBase {

  it(
    "includes visually similar images on a single image if we pass ?include=visuallySimilar") {
    withApi {
      case (ElasticConfig(_, imagesIndex), routes) =>
        val images = createVisuallySimilarImages(6)
        val image = images.head
        insertImagesIntoElasticsearch(imagesIndex, images: _*)
        assertJsonResponse(
          routes,
          s"/$apiPrefix/images/${images.head.id.canonicalId}?include=visuallySimilar",
          unordered = true) {
          Status.OK ->
            s"""
           |{
           |  $singleImageResult,
           |  "id": "${image.id.canonicalId}",
           |  "locations": [${digitalLocation(image.location)}],
           |  "visuallySimilar": [
           |    ${images.tail.map(imageResponse).mkString(",")}
           |  ],
           |  "source": {
           |    "id": "${image.source.id.canonicalId}",
           |    "type": "Work"
           |  }
           |}""".stripMargin
        }
    }
  }

  it("never includes visually similar images on an images search") {
    withApi {
      case (ElasticConfig(_, imagesIndex), routes) =>
        val focacciaImage = createAugmentedImageWith(
          id = "b",
          fullText = Some("A Ligurian style of bread, Focaccia")
        )
        insertImagesIntoElasticsearch(imagesIndex, focacciaImage)

        assertJsonResponse(
          routes,
          s"/$apiPrefix/images?query=focaccia&include=visuallySimilar") {
          Status.OK -> imagesListResponse(List(focacciaImage))
        }
    }
  }

}
