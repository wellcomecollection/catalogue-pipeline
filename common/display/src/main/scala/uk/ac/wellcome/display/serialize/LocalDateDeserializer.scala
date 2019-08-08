package uk.ac.wellcome.display.serialize

import java.time.LocalDate
import java.time.format.DateTimeParseException
import com.fasterxml.jackson.core.{JsonParser, JsonProcessingException}
import com.fasterxml.jackson.databind.{DeserializationContext, JsonDeserializer}
import com.fasterxml.jackson.databind.module.SimpleModule

class LocalDateParsingException(msg: String)
    extends JsonProcessingException(msg: String)

class LocalDateDeserializer extends JsonDeserializer[LocalDate] {
  override def deserialize(p: JsonParser,
                           ctx: DeserializationContext): LocalDate = {
    try {
      LocalDate.parse(p.getText())
    } catch {
      case exc: DateTimeParseException =>
        throw new LocalDateParsingException(exc.getMessage())
    }
  }
}

class LocalDateDeserializerModule extends SimpleModule {
  addDeserializer(classOf[LocalDate], new LocalDateDeserializer())
}
