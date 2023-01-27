package weco.catalogue.tei.id_extractor

import scala.util.{Failure, Try}
import scala.xml.XML

object IdExtractor {
  // Extracts an id from the root node of a TEI file.
  // The root node looks like <TEI xmlns="http://www.tei-c.org/ns/1.0" xml:id="manuscript_15651">
  // and we want to extract the value of the xml:id attribute, "manuscript_15651" in the example
  def extractId(blobContent: String, path: String): Try[String] =
    Try {
      val xml = XML.loadString(blobContent)
      xml.attributes
        .collectFirst {
          case metadata if metadata.key == "id" => metadata.value.text
        }
        .getOrElse(
          throw new RuntimeException(s"Could not find an id in XML at $path")
        )
    }.recoverWith { case th =>
      Failure(
        new RuntimeException(s"Unable to extract ID from XML at $path", th)
      )
    }

}
