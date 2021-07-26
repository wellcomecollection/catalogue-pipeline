package weco.pipeline.merger.rules

class DefaultTargetPrecedenceTest extends BaseTargetPrecedenceTest {
  override val targetPrecedence: BaseTargetPrecedence = DefaultTargetPrecedence

  it("doesn't chooses a Tei work") {
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
      .value shouldBe calm
  }
}
