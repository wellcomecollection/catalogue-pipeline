package weco.pipeline.transformer.tei

import grizzled.slf4j.Logging
import weco.catalogue.internal_model.identifiers.IdState.{Unidentifiable, Unminted}
import weco.catalogue.internal_model.work.{ContributionRole, Contributor, Person}
import weco.pipeline.transformer.result.Result

import scala.util.Try
import scala.xml.{Elem, XML}
class TeiXml(val xml: Elem) extends Logging {
  val id: String =
    getId.getOrElse(throw new RuntimeException(s"Could not find an id in XML!"))

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
  def bNumber: Result[Option[String]] = {
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

  def summary: Result[Option[String]] = TeiOps.summary(xml \\ "msDesc")

  /**
    * In an XML like this:
    * <TEI xmlns="http://www.tei-c.org/ns/1.0" xml:id="manuscript_15651">
    *  <teiHeader>
    *    <fileDesc>
    *      <publicationStmt>
    *        <idno type="msID">Well. Jav. 4</idno>
    *       </publicationStmt>
    * Extract "Well. Jav. 4" as the title
    */
  def title: Result[String] = {
    val nodes =
      (xml \ "teiHeader" \ "fileDesc" \ "publicationStmt" \ "idno").toList
    val maybeTitles = nodes.filter(n => (n \@ "type") == "msID")
    maybeTitles match {
      case List(titleNode) => Right(titleNode.text)
      case Nil             => Left(new RuntimeException("No title found!"))
      case _               => Left(new RuntimeException("More than one title node!"))
    }
  }

  def scribes: List[Contributor[Unminted]] = (xml \\"physDesc" \ "handDesc" \ "handNote").toList.flatMap { n =>
        n.attribute("scribe") match {
          case Some(_) => List(Contributor(Unidentifiable, Person(n.text.trim), List(ContributionRole("scribe"))))
          case None => val nodes = (n \ "persName").filter(n => (n \@ "role") == "scr")
            nodes.map{ node =>
              Contributor(Unidentifiable,Person(node.text.trim), List(ContributionRole("scribe")))
            }.toList
        }
      }

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
