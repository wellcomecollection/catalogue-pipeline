package weco.pipeline.transformer.mets.transformer

import weco.catalogue.internal_model.identifiers.{
  IdentifierType,
  SourceIdentifier
}

object ImageUtils {
  def isThumbnail(fileReference: FileReference): Boolean =
    fileReference.mimeType match {
      case Some("application/pdf")         => true
      case Some(m) if m startsWith "image" => true
      case _                               => false
    }

  def isImage(fileReference: FileReference): Boolean =
    fileReference.mimeType match {
      case Some(m) if m startsWith "image" => true
      case _                               => false
    }

  def getImageSourceId(bnumber: String, fileId: String): SourceIdentifier =
    SourceIdentifier(
      identifierType = IdentifierType.METSImage,
      ontologyType = "Image",
      value = s"$bnumber/$fileId"
    )

  private final val thumbnailDim = "200"
  private final val thumbnailPathSuffix =
    s"full/!$thumbnailDim,$thumbnailDim/0/default.jpg"

  // The /thumbs URL is routed to DLCS which handles only images
  // other asset types are routed to the iiif-builder service at /thumb
  // See: https://github.com/wellcomecollection/iiif-builder/blob/master/docs/thumbnails.md
  val imagesThumbBaseUrl = "https://iiif.wellcomecollection.org/thumbs"
  val othersThumbBaseUrl = "https://iiif.wellcomecollection.org/thumb"

  def buildThumbnailUrl(
    bnumber: String,
    validThumbnailFile: FileReference
  ): Option[String] =
    validThumbnailFile.mimeType match {
      case Some(mimeType) if mimeType.startsWith("image/") =>
        Some(
          s"$imagesThumbBaseUrl/${validThumbnailFile.location}/$thumbnailPathSuffix"
        )
      case _ =>
        Some(s"$othersThumbBaseUrl/$bnumber")
    }

  def buildImageUrl(validImageFile: FileReference): Option[String] =
    Some(
      s"https://iiif.wellcomecollection.org/image/${validImageFile.location}/info.json"
    )
}
