package weco.catalogue.tei.id_extractor

import scala.util.{Failure, Try}
import scala.xml.XML

object IdExtractor {
  // Extracts an id from the root node of a TEI file.
  // The root node looks like <TEI xmlns="http://www.tei-c.org/ns/1.0" xml:id="manuscript_15651">
  // and we want to extract the value of the xml:id attribute, "manuscript_15651" in the example
  def extractId(blobContent: String, path: String): Try[String] =
    Try {
      // Some of the TEI file have a Byte Order Mark (https://en.wikipedia.org/wiki/Byte_order_mark)
      // at the beginning, probably added by one of the tools used to edit the tei files.
      // This is a very hacky solution stolen from https://stackoverflow.com/questions/26847500/remove-bom-from-string-in-java
      val xml = XML.loadString(blobContent.replace("\uFEFF", ""))
      xml.attributes
        .collectFirst {
          case metadata if metadata.key == "id" => metadata.value.text
        }
        .getOrElse(
          throw new RuntimeException(s"Could not find an id in XML at $path"))
    }.recoverWith {
      case th =>
        Failure(
          new RuntimeException(s"Unable to extract ID from XML at $path", th))
    }

}
