package weco.pipeline.transformer.tei.transformers

import grizzled.slf4j.Logging
import weco.catalogue.internal_model.identifiers.ReferenceNumber

import scala.xml.Elem

object TeiReferenceNumber extends Logging {

  /** The reference number is in an <idno type="msID"> block, if present.
    *
    * e.g.
    *
    * <TEI xmlns="http://www.tei-c.org/ns/1.0" xml:id="manuscript_16046">
    *   <teiHeader>
    *     <fileDesc>
    *       <publicationStmt>
    *         <idno>UkLW</idno>
    *         <idno type="msID">WMS_Arabic_404</idno>
    *         <idno type="catalogue">Fihrist</idno>
    *       </publicationStmt>
    *
    * Note: at time of writing (16 November 2021), it was sufficient to look
    * for the <idno> node without considering the surrounding context.
    */
  def apply(xml: Elem): Option[ReferenceNumber] = {
    val ids =
      (xml \\ "idno")
        .map(n => (n \@ "type", n.text))
        .collect { case ("msID", contents) => contents.trim }

    ids match {
      case Seq(refNo) => Some(ReferenceNumber(refNo))
      case Nil        => None
      case _          =>
        warn(s"Could not find unambiguous reference number in $ids")
        None
    }
  }
}
