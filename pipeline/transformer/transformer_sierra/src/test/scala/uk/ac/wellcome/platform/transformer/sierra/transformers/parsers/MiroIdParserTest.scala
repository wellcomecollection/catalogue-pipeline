package uk.ac.wellcome.platform.transformer.sierra.transformers.parsers

import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.{FunSpec, Matchers}

class MiroIdParserTest extends FunSpec with Matchers with MiroIdParser {

  it("parses miroIds") {
    forAll(
      Table(
        ("miroId", "parsedId"),
        ("V1234567", "V1234567"),
        ("V 1234567", "V1234567"),
        ("V 1", "V0000001"),
        ("V 12345", "V0012345"),
        ("L 12345", "L0012345")
      )) { (miroId, expectedParsedId) =>
      parse089MiroId(miroId) shouldBe Some(expectedParsedId)
    }
  }

  it("does not parse invalid miroIds") {
    forAll(
      Table(
        "miroId",
        "",
        "11",
        "VV"
      )) { invalidMiroId =>
      parse089MiroId(invalidMiroId) shouldBe None
    }
  }
}
