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
}
