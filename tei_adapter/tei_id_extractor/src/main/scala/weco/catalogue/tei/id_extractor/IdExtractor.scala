package weco.catalogue.tei.id_extractor

import scala.util.Try
import scala.xml.XML

class IdExtractor {
  def extractId(blobContent: String) : Try[String]= {
    Try{
      val xml =XML.loadString(blobContent)
      xml.attributes.collectFirst {
        case metadata if metadata.key == "id" => metadata.value.text
      }.get
    }
  }

}
