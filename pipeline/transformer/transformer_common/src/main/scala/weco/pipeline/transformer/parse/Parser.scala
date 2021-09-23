package weco.pipeline.transformer.parse

import fastparse._
import grizzled.slf4j.Logging
import weco.catalogue.internal_model.work.InstantRange

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
      case Parsed.Failure(_, _, _)  => None
    }
}

/**
  *  Parser implementations intended to be used as implicit parameters
  */
package object parsers {
  implicit val DateParser: Parser[InstantRange] = PeriodParser
}
