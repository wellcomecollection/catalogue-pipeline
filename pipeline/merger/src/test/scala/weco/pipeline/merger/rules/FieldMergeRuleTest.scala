package weco.pipeline.merger.rules

import org.scalatest.matchers.should.Matchers
import cats.data.NonEmptyList
import org.scalatest.funspec.AnyFunSpec
import WorkPredicates.WorkPredicate
import weco.catalogue.internal_model.work.Work
import weco.catalogue.internal_model.work.WorkState.Identified
import weco.catalogue.internal_model.work.generators.WorkGenerators
import weco.pipeline.merger.models.FieldMergeResult

class FieldMergeRuleTest
    extends AnyFunSpec
    with Matchers
    with FieldMergeRule
    with WorkGenerators {

  override protected type FieldData = Unit

  val targetTitleIsA = new PartialRule {
    override val isDefinedForTarget: WorkPredicate =
      work => work.data.title.contains("A")
    override val isDefinedForSource: WorkPredicate = _ => true

    override def rule(
      target: Work.Visible[Identified],
      sources: NonEmptyList[Work[Identified]]
    ): FieldData =
      ()
  }
  val sourceTitleIsA = new PartialRule {
    override val isDefinedForTarget: WorkPredicate = _ => true
    override val isDefinedForSource: WorkPredicate =
      work => work.data.title.contains("A")

    override def rule(
      target: Work.Visible[Identified],
      sources: NonEmptyList[Work[Identified]]
    ): FieldData =
      ()
  }

  val workWithTitleA = identifiedWork().title("A")
  val workWithTitleB = identifiedWork().title("B")

  describe("PartialRule") {
    it(
      "is a partial function that is defined only for targets satisfying isDefinedForTarget"
    ) {
      targetTitleIsA(
        workWithTitleA,
        List(workWithTitleB)
      ).isDefined shouldBe true
      targetTitleIsA(
        workWithTitleB,
        List(workWithTitleB)
      ).isDefined shouldBe false
    }

    it(
      "is a partial function that is defined only if at least one source satisfies isDefinedForSource"
    ) {
      sourceTitleIsA(
        workWithTitleB,
        List(workWithTitleA, workWithTitleB)
      ).isDefined shouldBe true
      sourceTitleIsA(
        workWithTitleB,
        List(workWithTitleB)
      ).isDefined shouldBe false
    }

    it(
      "when applied, calls its rule method only for those sources satisfying isDefinedForSource"
    ) {
      val rule = new PartialRule {
        override val isDefinedForTarget: WorkPredicate = _ => true
        override val isDefinedForSource: WorkPredicate =
          work => work.data.title.contains("A")

        override def rule(
          target: Work.Visible[Identified],
          sources: NonEmptyList[Work[Identified]]
        ): FieldData = {
          sources.toList should contain(workWithTitleA)
          sources.toList should not contain workWithTitleB
          None
        }
      }

      rule(workWithTitleB, List(workWithTitleA, workWithTitleB))
    }
  }

  // This is here because we are extending ComposedFieldMergeRule
  // to access the private PartialRule trait
  override def merge(
    target: Work.Visible[Identified],
    sources: Seq[Work[Identified]]
  ): FieldMergeResult[FieldData] =
    throw new NotImplementedError()
}
