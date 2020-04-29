package uk.ac.wellcome.platform.api.images

import uk.ac.wellcome.display.models.v2.DisplayV2SerialisationTestBase
import uk.ac.wellcome.models.work.generators.ImageGenerators
import uk.ac.wellcome.platform.api.ApiTestBase

trait ApiImagesTestBase
    extends ApiTestBase
    with DisplayV2SerialisationTestBase
    with ImageGenerators {

  def singleImageResult: String =
    s"""
       |  "@context": "${contextUrl(apiPrefix)}",
       |  "type": "Image"
     """.stripMargin
}
