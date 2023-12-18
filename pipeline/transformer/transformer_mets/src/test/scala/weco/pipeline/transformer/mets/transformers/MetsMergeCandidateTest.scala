package weco.pipeline.transformer.mets.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.identifiers.IdentifierType

import scala.util.{Failure, Try}

class MetsMergeCandidateTest extends AnyFunSpec with Matchers {

  describe("extracting the merge candidate") {

    it("returns a Sierra merge candidate if the id is a b number") {
      MetsMergeCandidate(
        "b12345678"
      ).id.sourceIdentifier.identifierType shouldBe IdentifierType.SierraSystemNumber
    }

    it("returns a CALM merge candidate if the id is a CALM reference number") {
      MetsMergeCandidate(
        "A/BAAD/CAFE"
      ).id.sourceIdentifier.identifierType shouldBe IdentifierType.CalmRefNo

    }

    it(
      "can tell the difference between a b number and a b at the start of the id"
    ) {
      MetsMergeCandidate(
        "b/a/nana"
      ).id.sourceIdentifier.identifierType shouldBe IdentifierType.CalmRefNo
    }

    it("fails if the id type could not be determined") {
      Try(MetsMergeCandidate("!@£$%^&*()")) shouldBe a[Failure[_]]
    }

  }

}
