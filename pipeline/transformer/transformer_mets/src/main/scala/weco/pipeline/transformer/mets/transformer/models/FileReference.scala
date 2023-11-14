package weco.pipeline.transformer.mets.transformer.models

import java.net.URLConnection

case class FileReference(
  id: String,
  location: String,
  listedMimeType: Option[String] = None
) {
  // `guessContentTypeFromName` may still return a `null` (eg for `.jp2`)
  // because of the limited internal list of MIME types.
  lazy val mimeType: Option[String] =
    Option(
      listedMimeType.getOrElse(URLConnection.guessContentTypeFromName(location))
    )
}
