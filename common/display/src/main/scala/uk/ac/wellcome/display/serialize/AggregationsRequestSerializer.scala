package uk.ac.wellcome.display.serialize

import com.fasterxml.jackson.core.{JsonParser, JsonProcessingException}
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.{DeserializationContext, JsonDeserializer}
import uk.ac.wellcome.display.models._

class AggregationRequestParsingException(msg: String)
    extends JsonProcessingException(msg: String)

class AggregationsRequestDeserializer
    extends JsonDeserializer[List[AggregationRequest]] {

  override def deserialize(
    p: JsonParser,
    ctx: DeserializationContext): List[AggregationRequest] = {

    val aggregationsList = p.getText().split(",")
    val unrecognisedAggregations = aggregationsList
      .filterNot(AggregationsRequest.recognisedAggregations.contains)
    if (unrecognisedAggregations.isEmpty) {
      AggregationsRequest(aggregationsList)
    } else {
      val errorMessage = if (unrecognisedAggregations.length == 1) {
        s"'${unrecognisedAggregations.head}' is not a valid aggregation"
      } else {
        s"${unrecognisedAggregations.mkString("'", "', '", "'")} are not valid aggregations"
      }
      throw new AggregationRequestParsingException(errorMessage)
    }
  }
}

class AggregationsRequestDeserializerModule extends SimpleModule {
  addDeserializer(
    classOf[List[AggregationRequest]],
    new AggregationsRequestDeserializer())

}
