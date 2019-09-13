package uk.ac.wellcome.display.serialize

import com.fasterxml.jackson.core.JsonFactory
import org.scalatest.{FunSpec, Matchers}

case class InvalidADTStringKeyRequest(key: String)
    extends InvalidStringKeyException

trait ADTBase
case class ADT1() extends ADTBase
case class ADT2() extends ADTBase
case class ADT3() extends ADTBase
case class ADT4() extends ADTBase

object ADTBase {
  def apply(str: String): Either[InvalidADTStringKeyRequest, ADTBase] =
    str match {
      case "1" => Right(ADT1())
      case "2" => Right(ADT2())
      case "3" => Right(ADT3())
      case "4" => Right(ADT4())
      case _   => Left(InvalidADTStringKeyRequest(str))
    }
}

class CommaSeparatedStringRequestDeserializerTest
    extends FunSpec
    with Matchers {

  it("parses a present value as true") {
    val jf = new JsonFactory()
    val p = jf.createParser("\"1,2,3,4\"")
    p.nextValue()
    val parsed = CommaSeparatedStringRequestDeserializer.apply(p, ADTBase.apply)

    parsed shouldBe List(ADT1(), ADT2(), ADT3(), ADT4())
  }

  it("rejects an incorrect string") {
    intercept[CommaSeparatedStringRequestParsingException] {
      val jf = new JsonFactory()
      val p = jf.createParser("\"1,2,666,3\"")
      p.nextValue()
      CommaSeparatedStringRequestDeserializer.apply(p, ADTBase.apply)
    }
  }
}
