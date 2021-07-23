package weco.pipeline.merger.rules

import org.scalatest.OptionValues
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.work.Format
import weco.catalogue.internal_model.work.generators.SourceWorkGenerators



trait BaseTargetPrecedenceTest
    extends AnyFunSpec
    with Matchers
    with SourceWorkGenerators
    with OptionValues {
  val targetPrecedence: BaseTargetPrecedence

  val calm = calmIdentifiedWork()
  val videoSierra = sierraDigitalIdentifiedWork().format(Format.Videos)
  val multiItemPhysicalSierra = sierraIdentifiedWork().items(
    List(createIdentifiedPhysicalItem, createIdentifiedPhysicalItem)
  )
  val digitalSierra = sierraDigitalIdentifiedWork()
  val miro = miroIdentifiedWork()

  describe("target precedence is respected") {

    it("first, chooses a Calm work") {
      targetPrecedence
        .getTarget(
          Seq(calm, videoSierra, multiItemPhysicalSierra, digitalSierra, miro)
        )
        .value shouldBe calm
    }
    it("second, chooses a Sierra e-video") {
      targetPrecedence
        .getTarget(
          Seq(videoSierra, multiItemPhysicalSierra, digitalSierra, miro)
        )
        .value shouldBe videoSierra
    }
    it("third, chooses a physical Sierra work") {
      targetPrecedence
        .getTarget(
          Seq(multiItemPhysicalSierra, digitalSierra, miro)
        )
        .value shouldBe multiItemPhysicalSierra
    }
    it("finally, chooses any remaining Sierra work") {
      targetPrecedence
        .getTarget(
          Seq(digitalSierra, miro)
        )
        .value shouldBe digitalSierra
    }
  }

  it("returns None if no valid targets are present") {
    targetPrecedence.getTarget(Seq(miro)) shouldBe empty
  }

  it("can apply an additional predicate for target selection") {
    val works = Seq(multiItemPhysicalSierra, digitalSierra, miro)
    val nonPredicated = targetPrecedence.getTarget(works)
    val singleItemPredicated =
      targetPrecedence.targetSatisfying(WorkPredicates.singleItemSierra)(works)

    nonPredicated.value shouldBe multiItemPhysicalSierra
    singleItemPredicated.value shouldBe digitalSierra
  }
}
