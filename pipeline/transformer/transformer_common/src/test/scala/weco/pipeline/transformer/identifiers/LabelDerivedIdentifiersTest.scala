package weco.pipeline.transformer.identifiers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class LabelDerivedIdentifiersTest extends AnyFunSpec with Matchers {
  val identifiers = new LabelDerivedIdentifiers {}

  it(
    "removes whitespace that's added after we filter out non-ASCII characters"
  ) {
    // This comes from b16631614
    val label = "Miki\u0107, \u017delimir \u0110."
    val identifier =
      identifiers.identifierFromText(label = label, ontologyType = "Person")
    identifier.sourceIdentifier.value shouldBe "mikic, zelimir"
  }

  it("truncates label-derived identifiers to 255 characters") {
    // This comes from b28117293
    val label =
      "National Insurance Act, 1911 : explained, annotated and indexed, with appendices consisting of the Insurance Commissioners' official explanatory leaflets, Treasury regulations for the joint committee, tables of reserve values and voluntary contributions, regulations for procedure by the umpire, etc."
    val identifier =
      identifiers.identifierFromText(
        label = label,
        ontologyType = "Organisation"
      )

    // truncated at 255 chars
    identifier.sourceIdentifier.value shouldBe "national insurance act, 1911 : explained, annotated and indexed, with appendices consisting of the insurance commissioners' official explanatory leaflets, treasury regulations for the joint committee, tables of reserve values and voluntary contributions,"
  }
}
