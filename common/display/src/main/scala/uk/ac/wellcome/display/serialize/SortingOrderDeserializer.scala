package uk.ac.wellcome.display.serialize

import uk.ac.wellcome.display.models.SortingOrder

import com.fasterxml.jackson.core.{JsonParser, JsonProcessingException}
import com.fasterxml.jackson.databind.{DeserializationContext, JsonDeserializer}
import com.fasterxml.jackson.databind.module.SimpleModule

class SortingOrderParsingException(msg: String)
    extends JsonProcessingException(msg: String)

class SortingOrderDeserializer extends JsonDeserializer[SortingOrder] {
  override def deserialize(p: JsonParser,
                           ctx: DeserializationContext): SortingOrder = {
    p.getText() match {
      case "asc"  => SortingOrder.Ascending
      case "desc" => SortingOrder.Descending
      case text =>
        throw new SortingOrderParsingException(
          s"Invalid value ${text}. Choose 'desc' or 'asc'"
        )
    }
  }
}

class SortingOrderDeserializerModule extends SimpleModule {
  addDeserializer(classOf[SortingOrder], new SortingOrderDeserializer())
}
