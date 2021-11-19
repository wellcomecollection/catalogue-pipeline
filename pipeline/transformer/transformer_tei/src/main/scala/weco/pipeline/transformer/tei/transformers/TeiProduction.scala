package weco.pipeline.transformer.tei.transformers

import weco.catalogue.internal_model.identifiers.IdState.Unminted
import weco.catalogue.internal_model.work.{Organisation, Place, ProductionEvent}
import weco.pipeline.transformer.transformers.ParsedPeriod

import scala.xml.{Elem, NodeSeq}

object TeiProduction {
  def apply(xml: Elem): List[ProductionEvent[Unminted]] = apply(xml \\ "msDesc" \ "history" \ "origin")

  def apply(node: NodeSeq): List[ProductionEvent[Unminted]] =
    origin(node)

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
  private def origin(origin: NodeSeq): List[ProductionEvent[Unminted]] = {
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
      case (Nil, Nil, Nil) => Nil
      case _ =>

          List(
            ProductionEvent(
              label = label,
              places = places,
              agents = agents,
              dates = dates
            ))
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
}
