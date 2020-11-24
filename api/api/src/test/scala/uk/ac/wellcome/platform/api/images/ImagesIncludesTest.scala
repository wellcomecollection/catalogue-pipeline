package uk.ac.wellcome.platform.api.images

import uk.ac.wellcome.models.work.generators.ContributorGenerators
import uk.ac.wellcome.models.work.internal.Language

class ImagesIncludesTest extends ApiImagesTestBase with ContributorGenerators {
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
    val image = createAugmentedImageWith(parentWork = source)

    it(
      "includes the first source contributor on results from the list endpoint if we pass ?include=source.contributor") {
      withImagesApi {
        case (imagesIndex, routes) =>
          insertImagesIntoElasticsearch(imagesIndex, image)

          assertJsonResponse(
            routes,
            s"/$apiPrefix/images?include=source.contributor") {
            Status.OK -> s"""
              |{
              |  ${resultList(apiPrefix, totalResults = 1)},
              |  "results": [
              |    {
              |      "type": "Image",
              |      "id": "${image.id.canonicalId}",
              |      "locations": [${location(image.location)}],
              |      "source": {
              |        "id": "${source.id}",
              |        "title": "Apple agitator",
              |        "contributor": ${contributor(
                              source.data.contributors.head)},
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
      "includes the source contributor on a result from the single image endpoint if we pass ?include=source.contributor") {
      withImagesApi {
        case (imagesIndex, routes) =>
          insertImagesIntoElasticsearch(imagesIndex, image)

          assertJsonResponse(
            routes,
            s"/$apiPrefix/images/${image.id.canonicalId}?include=source.contributor") {
            Status.OK -> s"""
              |{
              |  $singleImageResult,
              |  "type": "Image",
              |  "id": "${image.id.canonicalId}",
              |  "locations": [${location(image.location)}],
              |  "source": {
              |    "id": "${source.id}",
              |    "title": "Apple agitator",
              |    "contributor": ${contributor(source.data.contributors.head)},
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
              |      "id": "${image.id.canonicalId}",
              |      "locations": [${location(image.location)}],
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
            s"/$apiPrefix/images/${image.id.canonicalId}?include=source.languages") {
            Status.OK -> s"""
              |{
              |  $singleImageResult,
              |  "type": "Image",
              |  "id": "${image.id.canonicalId}",
              |  "locations": [${location(image.location)}],
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
  }
}
