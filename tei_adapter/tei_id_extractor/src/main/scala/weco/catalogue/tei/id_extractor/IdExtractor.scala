package weco.catalogue.tei.id_extractor

import java.net.URI
import scala.util.Try
import scala.xml.XML

object IdExtractor {
  def extractId(blobContent: String, uri: URI): Try[String] = {
    Try {
      val xml = XML.loadString(blobContent)
      xml.attributes
        .collectFirst {
          case metadata if metadata.key == "id" => metadata.value.text
        }
        .getOrElse(throw new RuntimeException(
          s"Could not find an id in XML at ${uri.toString}"))
    }
  }

}
