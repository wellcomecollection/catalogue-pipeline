package uk.ac.wellcome.platform.api.images

import uk.ac.wellcome.display.models.DisplaySerialisationTestBase
import uk.ac.wellcome.models.work.generators.ImageGenerators
import uk.ac.wellcome.models.work.internal.AugmentedImage
import uk.ac.wellcome.platform.api.ApiTestBase

trait ApiImagesTestBase
    extends ApiTestBase
    with DisplaySerialisationTestBase
    with ImageGenerators {

  def singleImageResult: String =
    s"""
       |  "@context": "${contextUrl(apiPrefix)}",
       |  "type": "Image"
     """.stripMargin

  def imageResponse(image: AugmentedImage): String =
    s"""
       |  {
       |    "type": "Image",
       |    "id": "${image.id.canonicalId}",
       |    "location": ${location(image.location)},
       |    "parentWork": "${image.parentWork.canonicalId}"
       |  }
     """.stripMargin

  def imagesListResponse(images: Seq[AugmentedImage]): String =
    s"""
       |{
       |  ${resultList(apiPrefix, totalResults = images.size)},
       |  "results": [
       |    ${images.map(imageResponse).mkString(",")}
       |  ]
       |}
    """.stripMargin
}
