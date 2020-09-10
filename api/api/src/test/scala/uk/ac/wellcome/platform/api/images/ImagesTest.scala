package uk.ac.wellcome.platform.api.images

import uk.ac.wellcome.elasticsearch.ElasticConfig
import uk.ac.wellcome.elasticsearch.test.fixtures.ElasticsearchFixtures
import uk.ac.wellcome.models.work.internal.Identified

class ImagesTest extends ApiImagesTestBase with ElasticsearchFixtures {

  it("returns a list of images") {
    withApi {
      case (ElasticConfig(_, imagesIndex), routes) =>
        val images =
          (1 to 5).map(_ => createAugmentedImage()).sortBy(_.id.canonicalId)
        insertImagesIntoElasticsearch(imagesIndex, images: _*)
        assertJsonResponse(routes, s"/$apiPrefix/images") {
          Status.OK -> imagesListResponse(images)
        }
    }
  }

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
             |  "locations": [${digitalLocation(image.locationDeprecated)}],
             |  "source": {
             |    "id": "${image.source.id.canonicalId}",
             |    "type": "Work"
             |   }
             |}""".stripMargin
        }
    }
  }

  it("returns only linked images when a source work ID is requested") {
    withApi {
      case (ElasticConfig(_, imagesIndex), routes) =>
        val parentWork = createIdentifiedSierraWorkWith()
        val workImages =
          (0 to 3)
            .map(_ => createAugmentedImageWith(parentWork = parentWork))
            .toList
        val otherImage = createAugmentedImage()
        insertImagesIntoElasticsearch(imagesIndex, otherImage :: workImages: _*)
        assertJsonResponse(
          routes,
          s"/$apiPrefix/images?query=${parentWork.canonicalId}",
          unordered = true) {
          Status.OK -> imagesListResponse(workImages)
        }
        assertJsonResponse(
          routes,
          s"/$apiPrefix/images?query=${parentWork.sourceIdentifier.value}",
          unordered = true) {
          Status.OK -> imagesListResponse(workImages)
        }
    }
  }

  it("returns matching results when using work data") {
    withApi {
      case (ElasticConfig(_, imagesIndex), routes) =>
        val baguetteImage = createAugmentedImageWith(
          imageId = Identified("a", createSourceIdentifier),
          parentWork = createIdentifiedWorkWith(
            title = Some("Baguette is a French bread style"))
        )
        val focacciaImage = createAugmentedImageWith(
          imageId = Identified("b", createSourceIdentifier),
          parentWork = createIdentifiedWorkWith(
            title = Some("A Ligurian style of bread, Focaccia"))
        )
        val mantouImage = createAugmentedImageWith(
          imageId = Identified("c", createSourceIdentifier),
          parentWork = createIdentifiedWorkWith(
            title =
              Some("Mantou is a steamed bread associated with Northern China"))
        )
        insertImagesIntoElasticsearch(
          imagesIndex,
          baguetteImage,
          focacciaImage,
          mantouImage)

        assertJsonResponse(routes, s"/$apiPrefix/images?query=bread") {
          Status.OK -> imagesListResponse(
            List(baguetteImage, focacciaImage, mantouImage)
          )
        }
        assertJsonResponse(routes, s"/$apiPrefix/images?query=focaccia") {
          Status.OK -> imagesListResponse(List(focacciaImage))
        }
    }
  }

  it("returns matching results when using workdata from the redirected work") {
    withApi {
      case (ElasticConfig(_, imagesIndex), routes) =>
        val baguetteImage = createAugmentedImageWith(
          imageId = Identified("a", createSourceIdentifier),
          parentWork = createIdentifiedWorkWith(
            title = Some("Baguette is a French bread style"))
        )
        val focacciaImage = createAugmentedImageWith(
          imageId = Identified("b", createSourceIdentifier),
          parentWork = createIdentifiedWorkWith(
            title = Some("A Ligurian style of bread, Focaccia"))
        )
        val schiacciataImage = createAugmentedImageWith(
          imageId = Identified("c", createSourceIdentifier),
          parentWork = createIdentifiedWorkWith(
            title = Some("Schiacciata is a Tuscan focaccia")),
          redirectedWork =
            Some(createIdentifiedWorkWith(title = Some("A Tuscan bread")))
        )
        insertImagesIntoElasticsearch(
          imagesIndex,
          baguetteImage,
          focacciaImage,
          schiacciataImage)

        assertJsonResponse(routes, s"/$apiPrefix/images?query=bread") {
          Status.OK -> imagesListResponse(
            List(baguetteImage, focacciaImage, schiacciataImage)
          )
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
                 |  "locations": [${location(defaultImage.locationDeprecated)}],
                 |  "source": {
                 |    "id": "${defaultImage.source.id.canonicalId}",
                 |    "type": "Work"
                 |  }
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
                 |  "locations": [${location(
                   alternativeImage.locationDeprecated)}],
                 |  "source": {
                 |    "id": "${alternativeImage.source.id.canonicalId}",
                 |    "type": "Work"
                 |  }
                 |}""".stripMargin
          }
        }
    }
  }
}
