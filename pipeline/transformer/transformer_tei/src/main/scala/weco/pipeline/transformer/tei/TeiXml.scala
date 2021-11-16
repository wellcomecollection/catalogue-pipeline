package weco.pipeline.transformer.tei

import grizzled.slf4j.Logging
import weco.catalogue.internal_model.identifiers.IdState.Unminted
import weco.catalogue.internal_model.work.{Organisation, Place, ProductionEvent}
import weco.pipeline.transformer.result.Result
import weco.pipeline.transformer.tei.transformers.{
  TeiContributors,
  TeiLanguages,
  TeiNestedData,
  TeiPhysicalDescription,
  TeiReferenceNumber,
  TeiSubjects
}
import weco.pipeline.transformer.transformers.ParsedPeriod

import scala.util.Try
import scala.xml.{Elem, NodeSeq, XML}
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
        origin = origin,
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

  /**
    * The origin tag contains information about where and when
    * the manuscript was written. This is an example:
    *  <history>
    *      <origin>
    *          <origPlace>
    *              <country><!-- insert --></country>,
    *              <region><!-- insert --></region>,
    *              <settlement><!-- insert --></settlement>,
    *              <orgName><!-- insert --></orgName>
    *          </origPlace>
    *          <origDate calendar=""><!-- insert --></origDate>
    *      </origin>
    *  </history>
    *
    */
  private def origin: Result[List[ProductionEvent[Unminted]]] = {
    val origin = xml \\ "history" \ "origin"
    val origPlace = origin \ "origPlace"
    val country = (origPlace \ "country").text.trim
    val region = (origPlace \ "region").text.trim
    val settlement = (origPlace \ "settlement").text.trim
    val organisation = (origPlace \ "orgName").text.trim
    val date = parseDate(origin)
    val place =
      List(country, region, settlement).filterNot(_.isEmpty).mkString(", ")
    val agents =
      if (organisation.isEmpty) Nil else List(Organisation(organisation))
    val places = if (place.isEmpty) Nil else List(Place(place))
    val dates = if (date.isEmpty) Nil else List(ParsedPeriod(date))
    val label = List(place, date).filterNot(_.isEmpty).mkString(", ")
    (agents, places, dates) match {
      case (Nil, Nil, Nil) => Right(Nil)
      case _ =>
        Right(
          List(
            ProductionEvent(
              label = label,
              places = places,
              agents = agents,
              dates = dates
            )))
    }

  }

  /**
    * Dates are in a origDate tag and can be in different calendars,
    * so we need to look for the one in the gregorian calendar.
    * Also, sometimes the date can contain notes, as in this example, so we need to strip them:
    * <origDate calendar="Gregorian">ca.1732-63AD
    *  <note>from watermarks</note>
    * </origDate>
    */
  private def parseDate(origin: NodeSeq) = {
    val dateNodes = (origin \ "origDate").filter(n =>
      (n \@ "calendar").toLowerCase == "gregorian")
    val date =
      if (dateNodes.exists(_.child.size > 1))
        dateNodes
          .flatMap(_.child)
          .collect { case node if node.label != "note" => node.text }
          .mkString
          .trim
      else dateNodes.text.trim
    date
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
