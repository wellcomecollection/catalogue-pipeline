package weco.pipeline.transformer.mets.transformer

import weco.catalogue.internal_model.identifiers.{
  IdentifierType,
  SourceIdentifier
}
import weco.pipeline.transformer.mets.transformer.models.FileReference

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

  def buildImageUrl(validImageFile: FileReference): Option[String] =
    Some(
      s"https://iiif.wellcomecollection.org/image/${validImageFile.location}/info.json"
    )
}
