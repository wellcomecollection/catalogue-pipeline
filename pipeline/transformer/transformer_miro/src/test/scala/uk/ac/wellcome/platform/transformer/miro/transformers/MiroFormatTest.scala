package uk.ac.wellcome.platform.transformer.miro.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class MiroFormatTest extends AnyFunSpec with Matchers {
  it("sets a Format of 'Digital Images'") {
    transformer.getFormat.isDefined shouldBe true
    transformer.getFormat.get.label shouldBe "Digital Images"
  }

  val transformer = new MiroFormat {}
}
