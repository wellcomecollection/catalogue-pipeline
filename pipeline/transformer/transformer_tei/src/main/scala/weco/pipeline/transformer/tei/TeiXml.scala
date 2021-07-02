package weco.pipeline.transformer.tei

import scala.util.Try
import scala.xml.{Elem, XML}

class TeiXml(xml: Elem) {
  val id: String = xml.attributes
    .collectFirst {
      case metadata if metadata.key == "id" => metadata.value.text
    }
    .getOrElse(throw new RuntimeException(s"Could not find an id in XML!"))

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
  def bNumber: Either[Throwable, Option[String]] = {
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

  /**
    * The summary of the TEI is in the `summary` node under `msContents`. There is supposed to be only one summary node
    * <TEI xmlns="http://www.tei-c.org/ns/1.0" xml:id="manuscript_15651">
    *    <teiHeader>
    *      <fileDesc>
    *        <sourceDesc>
    *          <msDesc xml:lang="en" xml:id="MS_Arabic_1">
    *            <msContents>
    *              <summary>1 copy of al-Qānūn fī al-ṭibb by Avicenna, 980-1037</summary>
    *    ...
    *    </TEI>
    *
    */
  def summary: Either[Throwable, Option[String]] = {
    val nodes = (xml \\ "msDesc" \ "msContents" \ "summary").toList
    nodes match {
      case List(node) => Right(Some(node.text))
      case Nil        => Right(None)
      case _          => Left(new RuntimeException("More than one summary node!"))
    }
  }
}

object TeiXml {
  def apply(id: String, xmlString: String): Either[Throwable, TeiXml] =
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
