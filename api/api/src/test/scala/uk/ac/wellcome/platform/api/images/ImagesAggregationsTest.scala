package uk.ac.wellcome.platform.api.images

import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.models.work.generators.GenreGenerators

class ImagesAggregationsTest extends ApiImagesTestBase with GenreGenerators {
  it("aggregates by license") {
    val ccByImages = (1 to 5).map { _ =>
      createLicensedImage(License.CCBY)
    }

    val pdmImages = (1 to 2).map { _ =>
      createLicensedImage(License.PDM)
    }

    val images = ccByImages ++ pdmImages

    withImagesApi {
      case (imagesIndex, routes) =>
        insertImagesIntoElasticsearch(imagesIndex, images: _*)

        assertJsonResponse(
          routes,
          s"/$apiPrefix/images?aggregations=locations.license") {
          Status.OK -> s"""
            {
              ${resultList(apiPrefix, totalResults = images.size)},
              "aggregations": {
                "type" : "Aggregations",
                "license": {
                  "type" : "Aggregation",
                  "buckets": [
                    {
                      "data" : ${license(License.CCBY)},
                      "count" : ${ccByImages.size},
                      "type" : "AggregationBucket"
                    },
                    {
                      "data" : ${license(License.PDM)},
                      "count" : ${pdmImages.size},
                      "type" : "AggregationBucket"
                    }
                  ]
                }
              },
              "results": [
                ${images
            .sortBy { _.state.canonicalId }
            .map(imageResponse)
            .mkString(",")}
              ]
            }
          """
        }
    }
  }

  it("aggregates by the canonical source's contributor agent labels") {
    val carrots = Contributor(agent = Agent("carrots"), roles = Nil)
    val parrots = Contributor(agent = Organisation("parrots"), roles = Nil)
    val parrotsMeeting = Contributor(agent = Meeting("parrots"), roles = Nil)
    val rats = Contributor(agent = Person("rats"), roles = Nil)

    val images = List(
      createImageData.toIndexedImageWith(
        parentWork = identifiedWork().contributors(List(carrots))
      ),
      createImageData.toIndexedImageWith(
        parentWork = identifiedWork().contributors(List(carrots, parrots)),
        redirectedWork =
          Some(identifiedWork().contributors(List(parrots, parrotsMeeting)))
      ),
      createImageData.toIndexedImageWith(
        parentWork =
          identifiedWork().contributors(List(carrots, parrotsMeeting)),
        redirectedWork = Some(identifiedWork().contributors(List(rats)))
      )
    )

    withImagesApi {
      case (imagesIndex, routes) =>
        insertImagesIntoElasticsearch(imagesIndex, images: _*)

        assertJsonResponse(
          routes,
          s"/$apiPrefix/images?aggregations=source.contributors.agent.label"
        ) {
          Status.OK -> s"""
            {
              ${resultList(apiPrefix, totalResults = images.size)},
              "aggregations": {
                "type" : "Aggregations",
                "source.contributors.agent.label": {
                  "type" : "Aggregation",
                  "buckets": [
                    {
                      "data": ${contributor(carrots)},
                      "count": 3,
                      "type": "AggregationBucket"
                    },
                    {
                      "data": ${contributor(parrotsMeeting)},
                      "count": 1,
                      "type": "AggregationBucket"
                    },
                    {
                      "data": ${contributor(parrots)},
                      "count": 1,
                      "type": "AggregationBucket"
                    }
                  ]
                }
              },
              "results": [
                ${images
            .sortBy { _.state.canonicalId }
            .map(imageResponse)
            .mkString(",")}
              ]
            }
          """
        }
    }
  }

  it("aggregates by the canonical source's genres") {
    val carrotCounselling =
      createGenreWith("Carrot counselling", concepts = Nil)
    val dodoDivination = createGenreWith("Dodo divination", concepts = Nil)
    val emuEntrepreneurship =
      createGenreWith("Emu entrepreneurship", concepts = Nil)
    val falconFinances = createGenreWith("Falcon finances", concepts = Nil)

    val carrotCounsellingImage = createImageData.toIndexedImageWith(
      parentWork = identifiedWork().genres(List(carrotCounselling))
    )
    val redirectedDodoDivinationImage = createImageData.toIndexedImageWith(
      redirectedWork = Some(identifiedWork().genres(List(dodoDivination)))
    )
    val carrotEmuFalconImage =
      createImageData.toIndexedImageWith(
        parentWork = identifiedWork().genres(
          List(emuEntrepreneurship, falconFinances, carrotCounselling))
      )

    val images = List(
      carrotCounsellingImage,
      redirectedDodoDivinationImage,
      carrotEmuFalconImage
    )

    withImagesApi {
      case (imagesIndex, routes) =>
        insertImagesIntoElasticsearch(imagesIndex, images: _*)

        assertJsonResponse(
          routes,
          s"/$apiPrefix/images?aggregations=source.genres.label"
        ) {
          Status.OK -> s"""
            {
              ${resultList(apiPrefix, totalResults = images.size)},
              "aggregations": {
                "type" : "Aggregations",
                "source.genres.label": {
                  "type" : "Aggregation",
                  "buckets": [
                    {
                      "data": ${genre(carrotCounselling)},
                      "count": 2,
                      "type": "AggregationBucket"
                    },
                    {
                      "data": ${genre(emuEntrepreneurship)},
                      "count": 1,
                      "type": "AggregationBucket"
                    },
                    {
                      "data": ${genre(falconFinances)},
                      "count": 1,
                      "type": "AggregationBucket"
                    }
                  ]
                }
              },
              "results": [
                ${images
            .sortBy { _.state.canonicalId }
            .map(imageResponse)
            .mkString(",")}
              ]
            }
          """
        }
    }
  }
}
