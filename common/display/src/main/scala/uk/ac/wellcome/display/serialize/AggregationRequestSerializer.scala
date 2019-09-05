package uk.ac.wellcome.display.serialize

import com.fasterxml.jackson.core.{JsonParser, JsonProcessingException}
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.{DeserializationContext, JsonDeserializer}
import uk.ac.wellcome.display.models._

class AggregationRequestParsingException(msg: String)
    extends JsonProcessingException(msg: String)

class AggregationRequestDeserializer
    extends JsonDeserializer[List[AggregationRequest]] {

  override def deserialize(
    p: JsonParser,
    ctx: DeserializationContext): List[AggregationRequest] = {

    // The value that comes through here is similar to `["foo,bar"]`
    // So we jump into the array and then grab that text
    p.nextValue()
    val commaSeparatedString = p.getText()

    val validations = commaSeparatedString
      .split(",")
      .toList
      .map(_.trim)
      .map(AggregationRequest(_))

    val valid = validations collect { case Right(r)  => r }
    val invalid = validations collect { case Left(l) => l }

    if (invalid.isEmpty) {
      valid
    } else {
      val invalidKeys = invalid map (_.key)
      val errorMessage = invalidKeys.size match {
        case 1 => s"'${invalidKeys.head}' is not a valid aggregation"
        case _ =>
          s"${invalidKeys.mkString("'", "', '", "'")} are not valid aggregations"
      }
      throw new AggregationRequestParsingException(errorMessage)
    }
  }
}

class AggregationsRequestDeserializerModule extends SimpleModule {
  addDeserializer(
    classOf[List[AggregationRequest]],
    new AggregationRequestDeserializer())

}
