package uk.ac.wellcome.platform.api.images

import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.models.Implicits._

class ImagesAggregationsTest extends ApiImagesTestBase {
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
    val carrots = Agent("carrots")
    val parrots = Organisation("parrots")
    val parrotsMeeting = Meeting("parrots")
    val rats = Person("rats")

    val images = List(
      createImageData.toIndexedImageWith(
        parentWork = identifiedWork().contributors(
          List(carrots).map(Contributor(_, roles = Nil)))
      ),
      createImageData.toIndexedImageWith(
        parentWork = identifiedWork().contributors(
          List(carrots, parrots).map(Contributor(_, roles = Nil))),
        redirectedWork = Some(
          identifiedWork().contributors(
            List(parrots, parrotsMeeting).map(Contributor(_, roles = Nil))))
      ),
      createImageData.toIndexedImageWith(
        parentWork = identifiedWork().contributors(
          List(carrots, parrotsMeeting).map(Contributor(_, roles = Nil))),
        redirectedWork = Some(
          identifiedWork().contributors(
            List(rats).map(Contributor(_, roles = Nil))))
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
                      "data": ${abstractAgent(carrots)},
                      "count": 3,
                      "type": "AggregationBucket"
                    },
                    {
                      "data": ${abstractAgent(parrotsMeeting)},
                      "count": 1,
                      "type": "AggregationBucket"
                    },
                    {
                      "data": ${abstractAgent(parrots)},
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
