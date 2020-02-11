package uk.ac.wellcome.platform.transformer.mets.transformer

import uk.ac.wellcome.models.work.internal.{IdentifierType, SourceIdentifier}

trait MetsDataImageUtils {
  protected def isThumbnail(fileReference: FileReference): Boolean =
    fileReference.mimeType match {
      case Some("application/pdf")         => true
      case Some(m) if m startsWith "image" => true
      case _                               => false
    }

  protected def isImage(fileReference: FileReference): Boolean =
    fileReference.mimeType match {
      case Some(m) if m startsWith "image" => true
      case _                               => false
    }

  protected def getImageSourceId(bnumber: String,
                                 fileId: String): SourceIdentifier =
    SourceIdentifier(
      identifierType = IdentifierType("mets-image"),
      ontologyType = "Image",
      value = s"$bnumber/$fileId"
    )

  private final val thumbnailDim = "200"

  protected def buildThumbnailUrl(
    bnumber: String,
    validThumbnailFile: FileReference): Option[String] = {
    validThumbnailFile.mimeType match {
      case Some("application/pdf") =>
        Some(
          s"https://wellcomelibrary.org/pdfthumbs/${bnumber}/0/${validThumbnailFile.location}.jpg")
      case _ =>
        Some(
          s"https://dlcs.io/thumbs/wellcome/5/${validThumbnailFile.location}/full/!$thumbnailDim,$thumbnailDim/0/default.jpg")
    }
  }

  protected def buildImageUrl(bnumber: String,
                              validImageFile: FileReference): Option[String] = {
    validImageFile.mimeType match {
      case _ =>
        Some(
          s"https://dlcs.io/iiif-img/wellcome/5/${validImageFile.location}/info.json")
    }
  }
}
