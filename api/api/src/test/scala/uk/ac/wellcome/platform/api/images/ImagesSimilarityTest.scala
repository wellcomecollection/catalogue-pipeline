package uk.ac.wellcome.platform.api.images

import uk.ac.wellcome.elasticsearch.ElasticConfig

class ImagesSimilarityTest extends ApiImagesTestBase {

  it(
    "includes visually similar images on a single image if we pass ?include=visuallySimilar") {
    withApi {
      case (ElasticConfig(_, imagesIndex), routes) =>
        val images =
          createSimilarImages(6, similarFeatures = true, similarPalette = true)
        val image = images.head
        insertImagesIntoElasticsearch(imagesIndex, images: _*)
        assertJsonResponse(
          routes,
          s"/$apiPrefix/images/${images.head.id.canonicalId}?include=visuallySimilar") {
          Status.OK ->
            s"""
               |{
               |  $singleImageResult,
               |  "id": "${image.id.canonicalId}",
               |  "locations": [${digitalLocation(image.locationDeprecated)}],
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

  it(
    "includes images with similar features on a single image if we pass ?include=withSimilarFeatures") {
    withApi {
      case (ElasticConfig(_, imagesIndex), routes) =>
        val images =
          createSimilarImages(6, similarFeatures = true, similarPalette = false)
        val image = images.head
        insertImagesIntoElasticsearch(imagesIndex, images: _*)
        assertJsonResponse(
          routes,
          s"/$apiPrefix/images/${images.head.id.canonicalId}?include=withSimilarFeatures") {
          Status.OK ->
            s"""
               |{
               |  $singleImageResult,
               |  "id": "${image.id.canonicalId}",
               |  "locations": [${digitalLocation(image.locationDeprecated)}],
               |  "withSimilarFeatures": [
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

  it(
    "includes images with similar color palettes on a single image if we pass ?include=withSimilarColors") {
    withApi {
      case (ElasticConfig(_, imagesIndex), routes) =>
        val images =
          createSimilarImages(6, similarFeatures = false, similarPalette = true)
        val image = images.head
        insertImagesIntoElasticsearch(imagesIndex, images: _*)
        assertJsonResponse(
          routes,
          s"/$apiPrefix/images/${images.head.id.canonicalId}?include=withSimilarColors",
          unordered = true) {
          Status.OK ->
            s"""
               |{
               |  $singleImageResult,
               |  "id": "${image.id.canonicalId}",
               |  "locations": [${digitalLocation(image.locationDeprecated)}],
               |  "withSimilarColors": [
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
          parentWork = createIdentifiedWorkWith(
            title = Some("A Ligurian style of bread, Focaccia"))
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
