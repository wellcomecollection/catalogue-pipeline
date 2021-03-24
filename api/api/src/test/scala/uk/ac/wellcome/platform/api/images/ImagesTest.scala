package uk.ac.wellcome.platform.api.images

import uk.ac.wellcome.models.work.generators.SierraWorkGenerators
import uk.ac.wellcome.models.Implicits._

class ImagesTest extends ApiImagesTestBase with SierraWorkGenerators {

  it("returns a list of images") {
    withImagesApi {
      case (imagesIndex, routes) =>
        val images =
          (1 to 5).map(_ => createImageData.toIndexedImage).sortBy(_.id)
        insertImagesIntoElasticsearch(imagesIndex, images: _*)
        assertJsonResponse(routes, s"/$apiPrefix/images") {
          Status.OK -> imagesListResponse(images)
        }
    }
  }

  it("returns a single image when requested with ID") {
    withImagesApi {
      case (imagesIndex, routes) =>
        val image = createImageData.toIndexedImage
        insertImagesIntoElasticsearch(imagesIndex, image)
        assertJsonResponse(routes, s"/$apiPrefix/images/${image.id}") {
          Status.OK ->
            s"""
             |{
             |  $singleImageResult,
             |  "id": "${image.id}",
             |  "thumbnail": ${location(image.state.derivedData.thumbnail)},
             |  "locations": [${locations(image.locations)}],
             |  "source": ${imageSource(image.source)}
             |}""".stripMargin
        }
    }
  }

  it("returns only linked images when a source work ID is requested") {
    withImagesApi {
      case (imagesIndex, routes) =>
        val parentWork = sierraIdentifiedWork()
        val workImages =
          (0 to 3)
            .map(_ =>
              createImageData.toIndexedImageWith(parentWork = parentWork))
            .toList
        val otherImage = createImageData.toIndexedImage
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
        assertJsonResponse(
          routes,
          s"/$apiPrefix/images?query=${parentWork.data.otherIdentifiers.head.value}",
          unordered = true) {
          Status.OK -> imagesListResponse(workImages)
        }
    }
  }

  val baguetteImage = createImageData.toIndexedImageWith(
    canonicalId = "a",
    parentWork = identifiedWork()
      .title(
        "Baguette is a French style of bread; it's a long, thin bread; other countries also make this bread")
  )
  val focacciaImage = createImageData.toIndexedImageWith(
    canonicalId = "b",
    parentWork = identifiedWork()
      .title(
        "A Ligurian style of bread, Focaccia is a flat Italian bread")
  )

  it("returns matching results when using work data") {
    withImagesApi {
      case (imagesIndex, routes) =>
        val mantouImage = createImageData.toIndexedImageWith(
          canonicalId = "c",
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
    withImagesApi {
      case (imagesIndex, routes) =>
        val schiacciataImage = createImageData.toIndexedImageWith(
          canonicalId = "c",
          parentWork = identifiedWork()
            .title("Schiacciata is a Tuscan focaccia"),
          redirectedWork = Some(
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
    withImagesApi {
      case (defaultIndex, routes) =>
        withLocalImagesIndex { alternativeIndex =>
          val defaultImage = createImageData.toIndexedImage
          val alternativeImage = createImageData.toIndexedImage
          insertImagesIntoElasticsearch(defaultIndex, defaultImage)
          insertImagesIntoElasticsearch(alternativeIndex, alternativeImage)

          assertJsonResponse(routes, s"/$apiPrefix/images/${defaultImage.id}") {
            Status.OK ->
              s"""
                 |{
                 |  $singleImageResult,
                 |  "id": "${defaultImage.id}",
                 |  "thumbnail": ${location(
                   defaultImage.state.derivedData.thumbnail)},
                 |  "locations": [${locations(defaultImage.locations)}],
                 |  "source": ${imageSource(defaultImage.source)}
                 }""".stripMargin
          }

          assertJsonResponse(
            routes,
            s"/$apiPrefix/images/${alternativeImage.id}?_index=${alternativeIndex.name}") {
            Status.OK ->
              s"""
                 |{
                 |  $singleImageResult,
                 |  "id": "${alternativeImage.id}",
                 |  "thumbnail": ${location(
                   alternativeImage.state.derivedData.thumbnail)},
                 |  "locations": [${locations(alternativeImage.locations)}],
                 |  "source": ${imageSource(alternativeImage.source)}
                 }""".stripMargin
          }
        }
    }
  }
}
