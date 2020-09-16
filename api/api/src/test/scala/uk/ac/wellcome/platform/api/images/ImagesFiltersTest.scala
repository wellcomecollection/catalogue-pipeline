package uk.ac.wellcome.platform.api.images

import uk.ac.wellcome.elasticsearch.ElasticConfig
import uk.ac.wellcome.models.work.internal.License

class ImagesFiltersTest extends ApiImagesTestBase {
  describe("filtering images by license") {
    val ccByImage = createLicensedImage(License.CCBY)
    val ccByNcImage = createLicensedImage(License.CCBYNC)

    it("filters by license") {
      withApi {
        case (ElasticConfig(_, imagesIndex), routes) =>
          insertImagesIntoElasticsearch(imagesIndex, ccByImage, ccByNcImage)
          assertJsonResponse(
            routes,
            s"/$apiPrefix/images?locations.license=cc-by") {
            Status.OK -> imagesListResponse(
              images = Seq(ccByImage)
            )
          }
      }
    }
  }

  describe("filtering images by color") {
    val redImage = createAugmentedImageWith(
      inferredData = createInferredData.map(
        _.copy(
          palette = List(
            "3/4",
            "3/4",
            "6/4",
            "6/4",
            "30/4",
            "18/4",
            "1/4",
            "4/6",
            "4/6",
            "34/6",
            "34/6",
            "130/6",
            "70/6",
            "2/6",
            "6/8",
            "6/8",
            "69/8",
            "69/8",
            "301/8",
            "181/8",
            "2/8"
          ))))
    val blueImage = createAugmentedImageWith(
      inferredData = createInferredData.map(_.copy(palette = List(
        "48/4",
        "48/4",
        "5/4",
        "5/4",
        "26/4",
        "32/4",
        "16/4",
        "180/6",
        "180/6",
        "50/6",
        "50/6",
        "93/6",
        "144/6",
        "36/6",
        "448/8",
        "448/8",
        "83/8",
        "83/8",
        "164/8",
        "320/8",
        "128/8"
      ))))

    it("filters by color") {
      withApi {
        case (ElasticConfig(_, imagesIndex), routes) =>
          insertImagesIntoElasticsearch(imagesIndex, redImage, blueImage)
          assertJsonResponse(routes, f"/$apiPrefix/images?colors=ff0000") {
            Status.OK -> imagesListResponse(
              images = Seq(redImage)
            )
          }
      }
    }

    it("filters by multiple colors") {
      withApi {
        case (ElasticConfig(_, imagesIndex), routes) =>
          insertImagesIntoElasticsearch(imagesIndex, redImage, blueImage)
          assertJsonResponse(
            routes,
            f"/$apiPrefix/images?colors=ff0000,0000ff",
            unordered = true) {
            Status.OK -> imagesListResponse(
              images = Seq(blueImage, redImage)
            )
          }
      }
    }
  }
}
