package weco.pipeline.transformer.marc_common.transformers.parsers

import fastparse.{AnyChar, P, Start}
import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work.{Period, ProductionEvent}
import weco.pipeline.transformer.parse.Parser
import fastparse._
import NoWhitespace._

/** Parses Marc 008 fields into ProductionEvent
  *
  * Spec: https://www.loc.gov/marc/bibliographic/bd008a.html
  */
object MarcProductionEventParser
    extends Parser[ProductionEvent[IdState.Unminted]] {

  def parser[_: P] =
    (Start ~ createdDate ~ Marc008DateParser.parser ~ MarcPlaceParser.parser.?)
      .map {
        case (instantRange, place) =>
          ProductionEvent(
            label = instantRange.label,
            agents = Nil,
            dates = List(
              Period(label = instantRange.label, range = instantRange)
            ),
            places = place.toList,
            function = None
          )
      }

  def createdDate[_: P] = AnyChar.rep(exactly = 6)
}
