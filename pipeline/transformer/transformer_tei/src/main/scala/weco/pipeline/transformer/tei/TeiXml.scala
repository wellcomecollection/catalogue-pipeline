package weco.pipeline.transformer.tei

import grizzled.slf4j.Logging
import weco.pipeline.transformer.result.Result
import weco.pipeline.transformer.tei.transformers._

import scala.util.Try
import scala.xml.{Elem, XML}
class TeiXml(val xml: Elem) extends Logging {
  val id: String =
    getId.getOrElse(throw new RuntimeException(s"Could not find an id in XML!"))

  lazy val scribesMap = TeiContributors.scribes(xml, id)

  def parse: Result[TeiData] =
    for {
      title <- title
      bNumber <- bNumber
      summary <- summary
      languageData <- TeiLanguages(xml)
      (languages, languageNotes) = languageData
      scribes <- scribesMap
      nestedData <- TeiNestedData.nestedTeiData(xml, title, scribes)
      referenceNumber <- TeiReferenceNumber(xml)
    } yield
      TeiData(
        id = id,
        title = title,
        bNumber = bNumber,
        referenceNumber = Some(referenceNumber),
        description = summary,
        languages = languages,
        notes = languageNotes,
        contributors = scribes.getOrElse(id, Nil),
        nestedTeiData = nestedData,
        origin = TeiProduction(xml),
        physicalDescription = TeiPhysicalDescription(xml),
        subjects = TeiSubjects(xml)
      )

  /**
    * All the identifiers of the TEI file are in a `msIdentifier` bloc.
    * We need the `altIdentifier` node where `type` is `Sierra.`
    * <TEI>
    *   <teiHeader>
    *     <fileDesc>
    *       <sourceDesc>
    *         <msDesc xml:lang="en" xml:id="MS_Arabic_1">
    *           <msIdentifier>
    *               <altIdentifier type="former">
    *                 <idno>WMS. Or. 1a (Iskandar)</idno>
    *               </altIdentifier>
    *               <altIdentifier type="former">
    *                 <idno>WMS. Or. 1a</idno>
    *                </altIdentifier>
    *               <altIdentifier type="Sierra">
    *                 <idno>b1234567</idno>
    *                </altIdentifier>
    *           </msIdentifier>
    *      ...
    * </TEI>
    *
    */
  private def bNumber: Result[Option[String]] = {
    val identifiersNodes = xml \\ "msDesc" \ "msIdentifier" \ "altIdentifier"
    val seq = (identifiersNodes.filter(
      n => (n \@ "type").toLowerCase == "sierra"
    ) \ "idno").toList
    seq match {
      case List(node) => Right(Some(node.text.trim))
      case Nil        => Right(None)
      case _          => Left(new RuntimeException("More than one sierra bnumber node!"))
    }
  }

  private def summary: Result[Option[String]] = TeiOps.summary(xml \\ "msDesc")

  /** For now, we use the reference number as the title.  We don't use
    * the <title> node because:
    *
    *    - On Arabic manuscripts, the <title> is just "Wellcome Library"
    *    - On other manuscripts, it's the reference number repeated
    */
  private def title: Result[String] =
    TeiReferenceNumber(xml).map(_.underlying)

  private def getId: Result[String] = TeiOps.getIdFrom(xml)
}

object TeiXml {
  def apply(id: String, xmlString: String): Result[TeiXml] =
    for {
      xml <- Try(XML.loadString(xmlString)).toEither
      teiXml = new TeiXml(xml)
      _ <- Try(
        require(
          teiXml.id == id,
          s"Supplied id $id did not match id in XML ${teiXml.id}"
        )
      ).toEither
    } yield teiXml
}
