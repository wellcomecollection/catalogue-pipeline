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

  def buildThumbnailUrl(bnumber: String,
                        validThumbnailFile: FileReference): Option[String] =
    validThumbnailFile.mimeType match {
      case Some("application/pdf") =>
        Some(
          s"https://wellcomelibrary.org/pdfthumbs/$bnumber/0/${validThumbnailFile.location}.jpg")
      case _ =>
        Some(
          s"https://dlcs.io/thumbs/wellcome/5/${validThumbnailFile.location}/full/!$thumbnailDim,$thumbnailDim/0/default.jpg")
    }

  def buildImageUrl(validImageFile: FileReference): Option[String] =
    Some(
      s"https://dlcs.io/iiif-img/wellcome/5/${validImageFile.location}/info.json")
}
