package weco.pipeline.transformer.tei.transformers

import grizzled.slf4j.Logging
import weco.catalogue.internal_model.identifiers.ReferenceNumber
import weco.pipeline.transformer.result.Result

import scala.xml.Elem

object TeiReferenceNumber extends Logging {

  /** The reference number is in an <idno type="msID"> block, e.g.
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
  def apply(xml: Elem): Result[ReferenceNumber] = {
    val ids =
      (xml \\ "idno")
        .map(n => (n \@ "type", n.text))
        .collect { case ("msID", contents) => contents.trim }

    ids match {
      case Seq(refNo) => Right(ReferenceNumber(refNo))
      case Nil        => Left(new RuntimeException("No <idno type='msID'> found!"))
      case other =>
        Left(
          new RuntimeException(
            s"Multiple instances of <idno type='msID'> found! $other"))
    }
  }
}
