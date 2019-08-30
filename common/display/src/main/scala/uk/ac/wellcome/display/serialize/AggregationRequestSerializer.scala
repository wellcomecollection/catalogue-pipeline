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

    // We create a (key: String, agg: Option[Aggregation]) tuple so we can error nicely telling people which values are invalid
    val validations: Map[Boolean, List[(String, Option[AggregationRequest])]] =
      commaSeparatedString
        .split(",")
        .toList
        .map(_.trim)
        .map(key => (key, AggregationRequest(key))) groupBy {
        case (_, maybeAggregationRequest) => maybeAggregationRequest.isEmpty
      }

    val valid = validations.getOrElse(false, Nil)
    val invalid = validations.getOrElse(true, Nil)

    if (invalid.isEmpty) {
      valid.flatMap {
        case (_, aggregation) => aggregation
      }
    } else {
      val invalidKeys = invalid map {
        case (key, _) => key
      }
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
