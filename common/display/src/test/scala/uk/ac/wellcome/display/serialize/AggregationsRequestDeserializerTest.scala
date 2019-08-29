package uk.ac.wellcome.display.serialize

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.databind.ObjectMapper
import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.display.models.{WorkTypeAggregationRequest}

class AggregationsRequestDeserializerTest extends FunSpec with Matchers {

  it("parses a present value as true") {

    val mapper = new ObjectMapper
    val jf = new JsonFactory()
    val p = jf.createParser("\"workType\"")

    p.nextValue()
    val deserializer = new AggregationsRequestDeserializer()
    val parsed = deserializer.deserialize(p, mapper.getDeserializationContext)

    parsed shouldBe List(WorkTypeAggregationRequest())
  }

  it("rejects an incorrect string") {
    intercept[AggregationRequestParsingException] {
      val deserializer = new AggregationsRequestDeserializer()
      val mapper = new ObjectMapper
      val jf = new JsonFactory()
      val p = jf.createParser("\"nothing to see here\"")
      p.nextValue()
      deserializer.deserialize(p, mapper.getDeserializationContext)
    }
  }
}
