package uk.ac.wellcome.platform.api.images

import uk.ac.wellcome.display.models.DisplaySerialisationTestBase
import uk.ac.wellcome.platform.api.ApiTestBase
import uk.ac.wellcome.platform.api.generators.ApiImageGenerators
import weco.catalogue.internal_model.image._

trait ApiImagesTestBase
    extends ApiTestBase
    with DisplaySerialisationTestBase
    with ApiImageGenerators {

  def singleImageResult: String =
    s"""
       |  "@context": "${contextUrl(apiPrefix)}",
       |  "type": "Image"
     """.stripMargin

  def imageSource(source: ImageSource): String =
    source match {
      case ParentWorks(work, _) =>
        s"""
             | {
             |   "id": "${source.id.canonicalId}",
             |   ${optionalString("title", work.data.title)}
             |   "type": "Work"
             | }
           """.stripMargin
    }

  def imageResponse(image: Image[ImageState.Indexed]): String =
    s"""
       |  {
       |    "type": "Image",
       |    "id": "${image.id}",
       |    "thumbnail": ${location(image.state.derivedData.thumbnail)},
       |    "locations": [${locations(image.locations)}],
       |    "source": ${imageSource(image.source)}
       |  }
     """.stripMargin

  def imagesListResponse(images: Seq[Image[ImageState.Indexed]]): String =
    s"""
       |{
       |  ${resultList(
         apiPrefix,
         totalResults = images.size,
         totalPages = if (images.nonEmpty) { 1 } else { 0 })},
       |  "results": [
       |    ${images.map(imageResponse).mkString(",")}
       |  ]
       |}
    """.stripMargin
}
