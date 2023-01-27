package weco.pipeline.transformer.sierra.transformers.parsers

import fastparse._, NoWhitespace._

import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work.{Period, ProductionEvent}
import weco.pipeline.transformer.parse.Parser

/** Parses Marc 008 fields into ProductionEvent
  *
  * Spec: https://www.loc.gov/marc/bibliographic/bd008a.html
  */
object Marc008Parser extends Parser[ProductionEvent[IdState.Unminted]] {

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
