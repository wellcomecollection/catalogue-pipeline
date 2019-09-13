package uk.ac.wellcome.display.serialize

import com.fasterxml.jackson.core.{JsonParser, JsonProcessingException}
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.{DeserializationContext, JsonDeserializer}
import uk.ac.wellcome.display.models._

class WorksIncludesParsingException(msg: String)
    extends JsonProcessingException(msg: String)

object WorksIncludesDeserializer {
  def apply[W <: WorksIncludes](queryParam: String,
                                recognisedIncludes: List[String],
                                workIncludes: List[String] => W): W = {
    val includesList = queryParam.split(",").toList
    val unrecognisedIncludes = includesList
      .filterNot(recognisedIncludes.contains)
    if (unrecognisedIncludes.isEmpty) {
      workIncludes(includesList)
    } else {
      val errorMessage = if (unrecognisedIncludes.length == 1) {
        s"'${unrecognisedIncludes.head}' is not a valid include"
      } else {
        s"${unrecognisedIncludes.mkString("'", "', '", "'")} are not valid includes"
      }
      throw new WorksIncludesParsingException(errorMessage)
    }
  }
}

class V2WorksIncludesDeserializer extends JsonDeserializer[WorksIncludes] {
  override def deserialize(p: JsonParser,
                           ctx: DeserializationContext): WorksIncludes = {
    WorksIncludesDeserializer(
      p.getText(),
      WorksIncludes.recognisedIncludes,
      WorksIncludes.apply)
  }
}

class WorksIncludesDeserializerModule extends SimpleModule {
  addDeserializer(classOf[WorksIncludes], new V2WorksIncludesDeserializer())
}
