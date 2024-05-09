package weco.catalogue.internal_model.identifiers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class SourceIdentifierTest extends AnyFunSpec with Matchers {
  it("has the correct toString value") {
    val sourceIdentifier = SourceIdentifier(
      identifierType = IdentifierType.MiroImageNumber,
      value = "A0001234",
      ontologyType = "Work"
    )

    sourceIdentifier.toString shouldBe "Work[miro-image-number/A0001234]"
  }

  it("fails creating a sourceIdentifier with trailing spaces in the value") {
    intercept[IllegalArgumentException] {
      SourceIdentifier(
        identifierType = IdentifierType.SierraSystemNumber,
        value = "b1234567  ",
        ontologyType = "Work"
      )
    }
  }
}
