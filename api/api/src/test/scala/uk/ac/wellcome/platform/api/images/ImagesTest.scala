package uk.ac.wellcome.platform.api.images

import uk.ac.wellcome.elasticsearch.ElasticConfig
import uk.ac.wellcome.models.work.generators.SierraWorkGenerators
import uk.ac.wellcome.models.work.internal._

class ImagesTest extends ApiImagesTestBase with SierraWorkGenerators {

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
             |  "locations": [${digitalLocation(image.location)}],
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
        val parentWork = sierraIdentifiedWork()
        val workImages =
          (0 to 3)
            .map(_ => createAugmentedImageWith(parentWork = parentWork))
            .toList
        val otherImage = createAugmentedImage()
        insertImagesIntoElasticsearch(imagesIndex, otherImage :: workImages: _*)
        assertJsonResponse(
          routes,
          s"/$apiPrefix/images?query=${parentWork.state.canonicalId}",
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
          imageId = IdState.Identified("a", createSourceIdentifier),
          parentWork = identifiedWork()
            .title("Baguette is a French bread style")
        )
        val focacciaImage = createAugmentedImageWith(
          imageId = IdState.Identified("b", createSourceIdentifier),
          parentWork = identifiedWork()
            .title("A Ligurian style of bread, Focaccia")
        )
        val mantouImage = createAugmentedImageWith(
          imageId = IdState.Identified("c", createSourceIdentifier),
          parentWork = identifiedWork()
            .title("Mantou is a steamed bread associated with Northern China")
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
          imageId = IdState.Identified("a", createSourceIdentifier),
          parentWork = identifiedWork()
            .title("Baguette is a French bread style")
        )
        val focacciaImage = createAugmentedImageWith(
          imageId = IdState.Identified("b", createSourceIdentifier),
          parentWork = identifiedWork()
            .title("A Ligurian style of bread, Focaccia")
        )
        val schiacciataImage = createAugmentedImageWith(
          imageId = IdState.Identified("c", createSourceIdentifier),
          parentWork = identifiedWork()
            .title("Schiacciata is a Tuscan focaccia"),
          redirectedWork =
            Some(
              identifiedWork().title("A Tusdan bread")
            )
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
                 |  "locations": [${location(defaultImage.location)}],
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
                 |  "locations": [${location(alternativeImage.location)}],
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
