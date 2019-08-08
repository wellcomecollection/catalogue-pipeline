package uk.ac.wellcome.models.parse

import fastparse._, NoWhitespace._

import uk.ac.wellcome.models.work.internal.{
  AbstractAgent,
  MaybeDisplayable,
  Period,
  ProductionEvent
}

/**
  *  Parses Marc 008 fields into ProductionEvent
  *
  *  Spec: https://www.loc.gov/marc/bibliographic/bd008a.html
  */
object Marc008Parser
    extends Parser[ProductionEvent[MaybeDisplayable[AbstractAgent]]] {

  def parser[_: P] =
    (Start ~ createdDate ~ Marc008DateParser.parser ~ MarcPlaceParser.parser.?)
      .map {
        case (instantRange, place) =>
          ProductionEvent(
            label = instantRange.label,
            agents = Nil,
            dates = Period(instantRange.label, Some(instantRange)) :: Nil,
            places = place.toList,
            function = None)
      }

  def createdDate[_: P] = AnyChar.rep(exactly = 6)
}
