package uk.ac.wellcome.display.serialize

import com.fasterxml.jackson.core.{JsonParser, JsonProcessingException}
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.{DeserializationContext, JsonDeserializer}
import uk.ac.wellcome.display.models.{
  AggregationRequest,
  AggregationsRequest,
  SortRequest,
  SortsRequest
}

trait InvalidStringKeyException {
  val key: String
}

class CommaSeparatedStringRequestParsingException(msg: String)
    extends JsonProcessingException(msg: String)

object CommaSeparatedStringRequestDeserializer {

  def apply[T](
    p: JsonParser,
    keyToType: String => Either[InvalidStringKeyException, T]): List[T] = {

    val commaSeparatedString = p.getText()
    val validations = commaSeparatedString
      .split(",")
      .toList
      .map(_.trim)
      .map(keyToType(_))

    val valid = validations collect { case Right(r)  => r }
    val invalid = validations collect { case Left(l) => l }

    if (invalid.isEmpty) {
      valid
    } else {
      val invalidKeys = invalid map (_.key)
      val errorMessage = invalidKeys.size match {
        case 1 =>
          s"'${invalidKeys.head}' is not a valid value"
        case _ =>
          s"${invalidKeys.mkString("'", "', '", "'")} are not valid values"
      }

      throw new CommaSeparatedStringRequestParsingException(errorMessage)
    }
  }
}

class AggregationsRequestDeserializer
    extends JsonDeserializer[AggregationsRequest] {
  override def deserialize(p: JsonParser,
                           ctx: DeserializationContext): AggregationsRequest = {
    val values =
      CommaSeparatedStringRequestDeserializer(p, AggregationRequest.apply)

    AggregationsRequest(values)
  }
}

class SortRequestDeserializer extends JsonDeserializer[SortsRequest] {
  override def deserialize(p: JsonParser,
                           ctx: DeserializationContext): SortsRequest = {
    val values = CommaSeparatedStringRequestDeserializer(p, SortRequest.apply)
    SortsRequest(values)
  }
}

class CommaSeparatedStringRequestDeserializerModule extends SimpleModule {
  addDeserializer(classOf[SortsRequest], new SortRequestDeserializer())
  addDeserializer(
    classOf[AggregationsRequest],
    new AggregationsRequestDeserializer())
}
