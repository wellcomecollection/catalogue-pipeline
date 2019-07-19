package uk.ac.wellcome.models.parse

import uk.ac.wellcome.models.work.internal.InstantRange

/**
  *  Trait for parsing some input into T with the FastParse library
  */
trait Parser[T] {

  /**
   *  Parse some input
   *
   *  @param input the input string
   *  @return Some(output) if parse was successful, None otherwise
   */
  def apply(input: String) : Option[T]
}

/**
  *  Parser implementations intended to be used as implicit parameters
  */
package object parsers {

  implicit val DateParser = new Parser[InstantRange] {
    def apply(input: String) : Option[InstantRange] =
      new FreeformDateParser(input).parser.run().toOption
    }
}
