package weco.pipeline.transformer.identifiers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class LabelDerivedIdentifiersTest extends AnyFunSpec with Matchers {
  val identifiers = new LabelDerivedIdentifiers {}

  it("removes whitespace that's added after we filter out non-ASCII characters") {
    // This comes from b16631614
    val label = "Miki\u0107, \u017delimir \u0110."
    val identifier = identifiers.identifierFromText(label = label, ontologyType = "Person")
    identifier.sourceIdentifier.value shouldBe "mikic, zelimir"
  }
}
