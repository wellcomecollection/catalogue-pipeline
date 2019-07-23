package uk.ac.wellcome.models.parse

import fastparse._, NoWhitespace._

import uk.ac.wellcome.models.work.internal.{ProductionEvent,
                                            Period,
                                            MaybeDisplayable,
                                            AbstractAgent}

/**
  *  Parses Marc 008 fields into ProductionEvent
  *
  *  Spec: https://www.loc.gov/marc/bibliographic/bd008a.html
  */
object Marc008Parser
 extends Parser[ProductionEvent[MaybeDisplayable[AbstractAgent]]] {

  def parser[_: P] =
    (Start ~ createdDate ~ Marc008DateParser.parser ~ MarcPlaceParser.parser.?)
      .map { case (instantRange, place) =>
        ProductionEvent(
          label = "",
          agents = Nil,
          dates = List(Period("", Some(instantRange))),
          places = place.toList,
          function = None)}

  def createdDate[_: P] = AnyChar.rep(exactly = 6)
}
