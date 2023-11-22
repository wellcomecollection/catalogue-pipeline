package weco.pipeline.transformer.mets.transformer

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

}
