package uk.ac.wellcome.platform.api.images

import uk.ac.wellcome.models.work.generators.{
  ContributorGenerators,
  GenreGenerators
}
import uk.ac.wellcome.models.work.internal.Language
import uk.ac.wellcome.models.Implicits._

class ImagesIncludesTest
    extends ApiImagesTestBase
    with ContributorGenerators
    with GenreGenerators {
  describe("images includes") {
    val source = identifiedWork()
      .title("Apple agitator")
      .languages(
        List(
          Language(label = "English", id = "en"),
          Language(label = "Turkish", id = "tur")
        ))
      .contributors(
        List(
          createPersonContributorWith("Adrian Aardvark"),
          createPersonContributorWith("Beatrice Buffalo")
        ))
      .genres(
        List(
          createGenreWith("Crumbly cabbages"),
          createGenreWith("Deadly durians")
        )
      )
    val image = createImageData.toIndexedImageWith(parentWork = source)

    it(
      "includes the source contributors on results from the list endpoint if we pass ?include=source.contributors") {
      withImagesApi {
        case (imagesIndex, routes) =>
          insertImagesIntoElasticsearch(imagesIndex, image)

          assertJsonResponse(
            routes,
            s"/$apiPrefix/images?include=source.contributors") {
            Status.OK -> s"""
              |{
              |  ${resultList(apiPrefix, totalResults = 1)},
              |  "results": [
              |    {
              |      "type": "Image",
              |      "id": "${image.id}",
              |      "thumbnail": ${location(image.state.derivedData.thumbnail)},
              |      "locations": [${locations(image.locations)}],
              |      "source": {
              |        "id": "${source.id}",
              |        "title": "Apple agitator",
              |        "contributors": [${contributors(source.data.contributors)}],
              |        "type": "Work"
              |      }
              |    }
              |  ]
              |}
            """.stripMargin
          }
      }
    }

    it(
      "includes the source contributors on a result from the single image endpoint if we pass ?include=source.contributors") {
      withImagesApi {
        case (imagesIndex, routes) =>
          insertImagesIntoElasticsearch(imagesIndex, image)

          assertJsonResponse(
            routes,
            s"/$apiPrefix/images/${image.id}?include=source.contributors") {
            Status.OK -> s"""
              |{
              |  $singleImageResult,
              |  "type": "Image",
              |  "id": "${image.id}",
              |  "thumbnail": ${location(image.state.derivedData.thumbnail)},
              |  "locations": [${locations(image.locations)}],
              |  "source": {
              |    "id": "${source.id}",
              |    "title": "Apple agitator",
              |    "contributors": [${contributors(source.data.contributors)}],
              |    "type": "Work"
              |  }
              |}
            """.stripMargin
          }
      }
    }

    it(
      "includes the source languages on results from the list endpoint if we pass ?include=source.languages") {
      withImagesApi {
        case (imagesIndex, routes) =>
          insertImagesIntoElasticsearch(imagesIndex, image)

          assertJsonResponse(
            routes,
            s"/$apiPrefix/images?include=source.languages") {
            Status.OK -> s"""
              |{
              |  ${resultList(apiPrefix, totalResults = 1)},
              |  "results": [
              |    {
              |      "type": "Image",
              |      "id": "${image.id}",
              |      "thumbnail": ${location(image.state.derivedData.thumbnail)},
              |      "locations": [${locations(image.locations)}],
              |      "source": {
              |        "id": "${source.id}",
              |        "title": "Apple agitator",
              |        "languages": [${languages(source.data.languages)}],
              |        "type": "Work"
              |      }
              |    }
              |  ]
              |}
            """.stripMargin
          }
      }
    }

    it(
      "includes the source languages on a result from the single image endpoint if we pass ?include=source.languages") {
      withImagesApi {
        case (imagesIndex, routes) =>
          insertImagesIntoElasticsearch(imagesIndex, image)

          assertJsonResponse(
            routes,
            s"/$apiPrefix/images/${image.id}?include=source.languages") {
            Status.OK -> s"""
              |{
              |  $singleImageResult,
              |  "type": "Image",
              |  "id": "${image.id}",
              |  "thumbnail": ${location(image.state.derivedData.thumbnail)},
              |  "locations": [${locations(image.locations)}],
              |  "source": {
              |    "id": "${source.id}",
              |    "title": "Apple agitator",
              |    "languages": [${languages(source.data.languages)}],
              |    "type": "Work"
              |  }
              |}
            """.stripMargin
          }
      }
    }

    it(
      "includes the source genres on results from the list endpoint if we pass ?include=source.genres") {
      withImagesApi {
        case (imagesIndex, routes) =>
          insertImagesIntoElasticsearch(imagesIndex, image)

          assertJsonResponse(
            routes,
            s"/$apiPrefix/images?include=source.genres") {
            Status.OK -> s"""
              |{
              |  ${resultList(apiPrefix, totalResults = 1)},
              |  "results": [
              |    {
              |      "type": "Image",
              |      "id": "${image.id}",
              |      "thumbnail": ${location(image.state.derivedData.thumbnail)},
              |      "locations": [${locations(image.locations)}],
              |      "source": {
              |        "id": "${source.id}",
              |        "title": "Apple agitator",
              |        "genres": [${genres(source.data.genres)}],
              |        "type": "Work"
              |      }
              |    }
              |  ]
              |}
            """.stripMargin
          }
      }
    }

    it(
      "includes the source genres on a result from the single image endpoint if we pass ?include=source.genres") {
      withImagesApi {
        case (imagesIndex, routes) =>
          insertImagesIntoElasticsearch(imagesIndex, image)

          assertJsonResponse(
            routes,
            s"/$apiPrefix/images/${image.id}?include=source.genres") {
            Status.OK -> s"""
              |{
              |  $singleImageResult,
              |  "type": "Image",
              |  "id": "${image.id}",
              |  "thumbnail": ${location(image.state.derivedData.thumbnail)},
              |  "locations": [${locations(image.locations)}],
              |  "source": {
              |    "id": "${source.id}",
              |    "title": "Apple agitator",
              |    "genres": [${genres(source.data.genres)}],
              |    "type": "Work"
              |  }
              |}
            """.stripMargin
          }
      }
    }
  }
}
