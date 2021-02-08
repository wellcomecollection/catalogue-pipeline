package uk.ac.wellcome.models.work.internal

case class DerivedImageData(
  thumbnail: DigitalLocation
)

object DerivedImageData {
  def apply(image: Image[_]): DerivedImageData =
    DerivedImageData(
      thumbnail = image.locations
        .find(_.locationType.id == "iiif-image")
        .getOrElse(
          // This should never happen
          throw new RuntimeException(
            s"No iiif-image (thumbnail) location found on image ${image.sourceIdentifier}")
        )
    )
}
