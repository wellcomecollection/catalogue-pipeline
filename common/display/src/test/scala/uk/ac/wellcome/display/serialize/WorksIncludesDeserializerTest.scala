package uk.ac.wellcome.display.serialize

import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.display.models.V2WorksIncludes

class WorksIncludesDeserializerTest extends FunSpec with Matchers {

  it("parses a present value as true") {
    val includes = WorksIncludesDeserializer(
      "identifiers",
      V2WorksIncludes.recognisedIncludes,
      V2WorksIncludes.apply)
    includes.identifiers shouldBe true
  }

  it("rejects an incorrect string") {
    intercept[WorksIncludesParsingException] {
      WorksIncludesDeserializer(
        "foo,bar",
        V2WorksIncludes.recognisedIncludes,
        V2WorksIncludes.apply)
    }
  }
}
