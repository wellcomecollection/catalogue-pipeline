package uk.ac.wellcome.display.models.v2

import uk.ac.wellcome.models.work.internal.AugmentedImage

case class DisplayImage(
  id: String,
  location: DisplayDigitalLocationV2,
  parentWork: String
)

object DisplayImage {

  def apply(image: AugmentedImage): DisplayImage =
    new DisplayImage(
      id = image.id.canonicalId,
      location = DisplayDigitalLocationV2(image.location),
      parentWork = image.parentWork.canonicalId
    )

}
