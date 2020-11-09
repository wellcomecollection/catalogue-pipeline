package uk.ac.wellcome.models.parse

import fastparse._
import grizzled.slf4j.Logging

/**
  *  Trait for parsing some input into T with the FastParse library
  */
trait Parser[T] extends Logging {

  /**
    *  The FastParse parser combinator applied to the input
    */
  def parser[_: P]: P[T]

  /**
    *  Parse some input
    *
    *  @param input the input string
    *  @return Some(output) if parse was successful, None otherwise
    */
  def apply(input: String): Option[T] =
    parse(input, parser(_)) match {
      case Parsed.Success(value, _) => Some(value)
      case Parsed.Failure(_, index, _) =>
        warn(s"Failed parsing $input at $index")
        None
    }
}

/**
  *  Parser implementations intended to be used as implicit parameters
  */
package object parsers {

  implicit val DateParser = FreeformDateParser
}
