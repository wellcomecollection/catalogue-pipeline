package uk.ac.wellcome.models.work.internal

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class SourceIdentifierTest extends AnyFunSpec with Matchers {
  it("has the correct toString value") {
    val sourceIdentifier = SourceIdentifier(
      identifierType = IdentifierType("miro-image-number"),
      value = "A0001234",
      ontologyType = "Work"
    )

    sourceIdentifier.toString shouldBe "miro-image-number/A0001234"
  }

  it("fails creating a sourceIdentifier with a trailing space in the value"){
    intercept[IllegalArgumentException]{
      SourceIdentifier(identifierType = IdentifierType("sierra-system-number"), value = "b1234567  ", ontologyType = "Work")
    }
  }
}
