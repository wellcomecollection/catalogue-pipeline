package weco.pipeline.merger.rules.tei

import weco.pipeline.merger.rules.{
  BaseTargetPrecedence,
  BaseTargetPrecedenceTest,
  TeiTargetPrecedence
}

class TeiTargetPrecedenceTest extends BaseTargetPrecedenceTest {
  override val targetPrecedence: BaseTargetPrecedence = TeiTargetPrecedence
  val tei = teiIdentifiedWork()
  it("first, chooses a Tei work") {
    targetPrecedence
      .getTarget(
        Seq(
          tei,
          calm,
          videoSierra,
          multiItemPhysicalSierra,
          digitalSierra,
          miro)
      )
      .value shouldBe tei
  }
}
