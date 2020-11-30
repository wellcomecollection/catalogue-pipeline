package uk.ac.wellcome.platform.api.images

import uk.ac.wellcome.display.models.DisplaySerialisationTestBase
import uk.ac.wellcome.models.work.generators.ImageGenerators
import uk.ac.wellcome.models.work.internal.{
  DataState,
  Image,
  ImageSource,
  ImageState,
  SourceWorks
}
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

  def imageSource(source: ImageSource[DataState.Identified]): String =
    source match {
      case SourceWorks(work, _) =>
        s"""
             | {
             |   "id": "${source.id.canonicalId}",
             |   ${optionalString("title", work.data.title)}
             |   "type": "Work"
             | }
           """.stripMargin
    }

  def imageResponse(image: Image[ImageState.Augmented]): String =
    s"""
       |  {
       |    "type": "Image",
       |    "id": "${image.id}",
       |    "locations": [${locations(image.locations)}],
       |    "source": ${imageSource(image.state.source)}
       |  }
     """.stripMargin

  def imagesListResponse(images: Seq[Image[ImageState.Augmented]]): String =
    s"""
       |{
       |  ${resultList(apiPrefix, totalResults = images.size)},
       |  "results": [
       |    ${images.map(imageResponse).mkString(",")}
       |  ]
       |}
    """.stripMargin
}
