package weco.pipeline.transformer.mets.transformers

import weco.catalogue.internal_model.locations.{
  AccessStatus,
  DigitalLocation,
  License,
  LocationType
}
import weco.pipeline.transformer.mets.transformer.models.FileReference

/** Constructs a DigitalLocation representing a thumbnail, if appropriate.
  *
  * A Location will only be returned if there is a reference for it, and if
  * there are no restrictions preventing it
  */
object MetsThumbnail extends DLCSFilenameNormaliser {

  def apply(
    thumbnailReference: Option[FileReference],
    bNumber: String,
    license: Option[License],
    accessStatus: Option[AccessStatus]
  ): Option[DigitalLocation] = {
    if (accessStatus.exists(_.hasRestrictions)) None
    else
      thumbnailReference map {
        fileReference =>
          DigitalLocation(
            url = buildThumbnailUrl(bNumber, fileReference),
            locationType = LocationType.ThumbnailImage,
            license = license
          )
      }
  }

  // The /thumbs URL is routed to DLCS which handles only images
  // other asset types are routed to the iiif-builder service at /thumb
  // See: https://github.com/wellcomecollection/iiif-builder/blob/master/docs/thumbnails.md
  val imagesThumbBaseUrl = "https://iiif.wellcomecollection.org/thumbs"
  val othersThumbBaseUrl = "https://iiif.wellcomecollection.org/thumb"
  private final val thumbnailDim = "200"
  private final val thumbnailPathSuffix =
    s"full/!$thumbnailDim,$thumbnailDim/0/default.jpg"

  private def buildThumbnailUrl(
    bNumber: String,
    validThumbnailFile: FileReference
  ): String =
    validThumbnailFile.mimeType match {
      case Some(mimeType) if mimeType.startsWith("image/") =>
        s"$imagesThumbBaseUrl/${normaliseLocation(bNumber, validThumbnailFile.location)}/$thumbnailPathSuffix"
      case _ => s"$othersThumbBaseUrl/$bNumber"
    }

}
