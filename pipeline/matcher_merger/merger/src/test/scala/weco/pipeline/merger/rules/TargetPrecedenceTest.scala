package weco.pipeline.merger.rules

import org.scalatest.OptionValues
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.work.Format
import weco.catalogue.internal_model.work.generators.SourceWorkGenerators

class TargetPrecedenceTest
    extends AnyFunSpec
    with Matchers
    with SourceWorkGenerators
    with OptionValues {

  val tei = teiIdentifiedWork()
  val calm = calmIdentifiedWork()
  val videoSierra = sierraDigitalIdentifiedWork().format(Format.Videos)
  val multiItemPhysicalSierra = sierraIdentifiedWork().items(
    List(createIdentifiedPhysicalItem, createIdentifiedPhysicalItem)
  )
  val digitalSierra = sierraDigitalIdentifiedWork()
  val miro = miroIdentifiedWork()

  describe("target precedence is respected") {
    it("first, chooses a Tei work") {
      TargetPrecedence
        .getTarget(
          Seq(
            calm,
            videoSierra,
            tei,
            multiItemPhysicalSierra,
            digitalSierra,
            miro
          )
        )
        .value shouldBe tei
    }
    it("second, chooses a Calm work") {
      TargetPrecedence
        .getTarget(
          Seq(videoSierra, multiItemPhysicalSierra, digitalSierra, calm, miro)
        )
        .value shouldBe calm
    }
    it("third, chooses a Sierra e-video") {
      TargetPrecedence
        .getTarget(
          Seq(multiItemPhysicalSierra, digitalSierra, miro, videoSierra)
        )
        .value shouldBe videoSierra
    }
    it("fourth, chooses a physical Sierra work") {
      TargetPrecedence
        .getTarget(
          Seq(digitalSierra, multiItemPhysicalSierra, miro)
        )
        .value shouldBe multiItemPhysicalSierra
    }
    it("finally, chooses any remaining Sierra work") {
      TargetPrecedence
        .getTarget(
          Seq(miro, digitalSierra)
        )
        .value shouldBe digitalSierra
    }

    describe("when multiple works have the same precedence") {
      it("chooses the first of them") {
        // This test exists to document existing behaviour.
        // If a better method for ensuring consistency is defined,
        // then this test should be revised.
        val digitalSierra2 = sierraDigitalIdentifiedWork()
        TargetPrecedence
          .getTarget(
            Seq(miro, digitalSierra, digitalSierra2)
          )
          .value shouldBe digitalSierra

        TargetPrecedence
          .getTarget(
            Seq(digitalSierra2, miro, digitalSierra)
          )
          .value shouldBe digitalSierra2

      }
    }
  }

  it("returns None if no valid targets are present") {
    TargetPrecedence.getTarget(Seq(miro)) shouldBe empty
  }

  it("can apply an additional predicate for target selection") {
    val works = Seq(multiItemPhysicalSierra, digitalSierra, miro)
    val nonPredicated = TargetPrecedence.getTarget(works)
    val singleItemPredicated =
      TargetPrecedence.targetSatisfying(WorkPredicates.singleItemSierra)(works)

    nonPredicated.value shouldBe multiItemPhysicalSierra
    singleItemPredicated.value shouldBe digitalSierra
  }
}
