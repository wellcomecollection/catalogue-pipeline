package uk.ac.wellcome.platform.transformer.miro.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class MiroWorkTypeTest extends AnyFunSpec with Matchers {
  it("sets a WorkType of 'Digital Images'") {
    transformer.getWorkType.isDefined shouldBe true
    transformer.getWorkType.get.label shouldBe "Digital Images"
  }

  val transformer = new MiroWorkType {}
}
