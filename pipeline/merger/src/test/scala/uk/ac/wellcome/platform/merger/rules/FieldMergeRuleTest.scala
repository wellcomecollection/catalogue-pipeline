package uk.ac.wellcome.platform.merger.rules

import org.scalatest.matchers.should.Matchers
import cats.data.NonEmptyList
import org.scalatest.funspec.AnyFunSpec
import uk.ac.wellcome.models.work.generators.WorksGenerators
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.merger.models.FieldMergeResult
import uk.ac.wellcome.platform.merger.rules.WorkPredicates.WorkPredicate
import WorkState.Unidentified

class FieldMergeRuleTest
    extends AnyFunSpec
    with Matchers
    with FieldMergeRule
    with WorksGenerators {
  override protected type FieldData = Unit

  val targetTitleIsA = new PartialRule {
    override val isDefinedForTarget: WorkPredicate =
      work => work.data.title.contains("A")
    override val isDefinedForSource: WorkPredicate = _ => true

    override def rule(target: Work.Visible[Unidentified],
                      sources: NonEmptyList[Work[Unidentified]]): FieldData =
      ()
  }
  val sourceTitleIsA = new PartialRule {
    override val isDefinedForTarget: WorkPredicate = _ => true
    override val isDefinedForSource: WorkPredicate =
      work => work.data.title.contains("A")

    override def rule(target: Work.Visible[Unidentified],
                      sources: NonEmptyList[Work[Unidentified]]): FieldData =
      ()
  }

  val workWithTitleA = createUnidentifiedWorkWith(title = Some("A"))
  val workWithTitleB = createUnidentifiedWorkWith(title = Some("B"))

  describe("PartialRule") {
    it(
      "is a partial function that is defined only for targets satisfying isDefinedForTarget") {
      targetTitleIsA(workWithTitleA, List(workWithTitleB)).isDefined shouldBe true
      targetTitleIsA(workWithTitleB, List(workWithTitleB)).isDefined shouldBe false
    }

    it(
      "is a partial function that is defined only if at least one source satisfies isDefinedForSource") {
      sourceTitleIsA(workWithTitleB, List(workWithTitleA, workWithTitleB)).isDefined shouldBe true
      sourceTitleIsA(workWithTitleB, List(workWithTitleB)).isDefined shouldBe false
    }

    it(
      "when applied, calls its rule method only for those sources satisfying isDefinedForSource") {
      val rule = new PartialRule {
        override val isDefinedForTarget: WorkPredicate = _ => true
        override val isDefinedForSource: WorkPredicate =
          work => work.data.title.contains("A")

        override def rule(
          target: Work.Visible[Unidentified],
          sources: NonEmptyList[Work[Unidentified]]): FieldData = {
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
    target: Work.Visible[Unidentified],
    sources: Seq[Work[Unidentified]]): FieldMergeResult[FieldData] =
    throw new NotImplementedError()
}
