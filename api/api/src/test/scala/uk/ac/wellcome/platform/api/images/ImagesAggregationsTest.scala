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
}
