package weco.pipeline.transformer.tei

import grizzled.slf4j.Logging
import weco.catalogue.internal_model.identifiers.IdState.Unminted
import weco.catalogue.internal_model.work.{Organisation, Place, ProductionEvent}
import weco.pipeline.transformer.result.Result
import weco.pipeline.transformer.tei.transformers.{TeiContributors, TeiLanguages, TeiNestedData}

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
      origin <- origin
    } yield
      TeiData(
        id = id,
        title = title,
        bNumber = bNumber,
        description = summary,
        languages = languages,
        languageNotes = languageNotes,
        contributors = scribes.getOrElse(id, Nil),
        nestedTeiData = nestedData,
        origin = origin
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
  private def title: Result[String] = {
    val nodes =
      (xml \ "teiHeader" \ "fileDesc" \ "publicationStmt" \ "idno").toList
    val maybeTitles = nodes.filter(n => (n \@ "type") == "msID")
    maybeTitles match {
      case List(titleNode) => Right(titleNode.text)
      case Nil             => Left(new RuntimeException("No title found!"))
      case _               => Left(new RuntimeException("More than one title node!"))
    }
  }

  private def origin: Result[List[ProductionEvent[Unminted]]] = {
    val origPlace = xml \\ "history" \ "origin" \ "origPlace"
    val country = (origPlace \ "country").text.trim
    val region = (origPlace \ "region").text.trim
    val settlement = (origPlace \ "settlement").text.trim
    val organisation = (origPlace \ "orgName").text.trim

    val label = List(country, region, settlement).filterNot(_.isEmpty).mkString(", ")
    val agents = if(organisation.isEmpty){
      Nil
    }else {
      List(Organisation(organisation))
    }
    Right(List(ProductionEvent(
      label = label,
      places = List(Place(label)),
      agents = agents,
      dates = Nil,
    )))
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
