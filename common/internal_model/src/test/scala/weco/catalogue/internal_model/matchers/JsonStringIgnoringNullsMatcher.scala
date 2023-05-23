package weco.catalogue.internal_model.matchers
import io.circe.Json
import io.circe.parser.parse
import org.scalatest.matchers.{MatchResult, Matcher}

trait JsonStringIgnoringNullsMatcher {

  class EqualIgnoringNullsMatcher(rhs: String) extends Matcher[String] {

    def apply(lhs: String): MatchResult = {
      val jsonL = parseOrElse(lhs)
      val jsonR = parseOrElse(rhs)

      MatchResult(
        jsonL.equals(jsonR),
        s"""$lhs did not match "$rhs"""",
        s"""$lhs matched "$rhs""""
      )
    }
  }

  def beTheSameJsonIgnoringNulls(expected: String) =
    new EqualIgnoringNullsMatcher(
      expected
    )

  private def parseOrElse(jsonString: String): Json =
    parse(jsonString) match {
      case Right(json) => json.deepDropNullValues
      case Left(err) =>
        println(s"Error trying to parse string <<$jsonString>>")
        throw err
    }
}
