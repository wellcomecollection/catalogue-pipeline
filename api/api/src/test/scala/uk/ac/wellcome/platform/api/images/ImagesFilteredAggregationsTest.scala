package uk.ac.wellcome.platform.api.images

import uk.ac.wellcome.models.Implicits._
import weco.catalogue.internal_model.locations.License

class ImagesFilteredAggregationsTest extends ApiImagesTestBase {
  it("filters and aggregates by license") {
    val ccByImages = (1 to 5).map { _ =>
      createLicensedImage(License.CCBY)
    }

    val pdmImages = (1 to 2).map { _ =>
      createLicensedImage(License.PDM)
    }

    val oglImages = (1 to 3).map { _ =>
      createLicensedImage(License.OGL)
    }

    val images = ccByImages ++ pdmImages ++ oglImages

    withImagesApi {
      case (imagesIndex, routes) =>
        insertImagesIntoElasticsearch(imagesIndex, images: _*)
        val expectedImages = ccByImages ++ pdmImages

        assertJsonResponse(
          routes,
          s"/$apiPrefix/images?aggregations=locations.license&locations.license=cc-by,pdm") {
          Status.OK -> s"""
            {
              ${resultList(apiPrefix, totalResults = expectedImages.size)},
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
                    ,
                    {
                      "data" : ${license(License.OGL)},
                      "count" : 0,
                      "type" : "AggregationBucket"
                    }
                  ]
                }
              },
              "results": [
                ${expectedImages
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
