package uk.ac.wellcome.platform.merger.rules

import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.models.work.generators.WorksGenerators
import uk.ac.wellcome.models.work.internal.{
  TransformedBaseWork,
  UnidentifiedWork
}
import uk.ac.wellcome.platform.merger.rules.WorkFilters.WorkFilter

class FieldMergeRuleTest
    extends FunSpec
    with Matchers
    with FieldMergeRule
    with WorksGenerators {
  override protected type FieldData = Option[String]

  val targetTitleIsA = new PartialRule {
    override val isDefinedForTarget: WorkFilter =
      work => work.data.title.contains("A")
    override val isDefinedForSource: WorkFilter = _ => true

    override def rule(target: UnidentifiedWork,
                      sources: Seq[TransformedBaseWork]): FieldData = None
  }
  val sourceTitleIsA = new PartialRule {
    override val isDefinedForTarget: WorkFilter = _ => true
    override val isDefinedForSource: WorkFilter =
      work => work.data.title.contains("A")

    override def rule(target: UnidentifiedWork,
                      sources: Seq[TransformedBaseWork]): FieldData = None
  }

  val workWithTitleA = createUnidentifiedWorkWith(title = Some("A"))
  val workWithTitleB = createUnidentifiedWorkWith(title = Some("B"))

  describe("PartialRule") {
    it(
      "is a partial function that is defined only for targets satisfying isDefinedForTarget") {
      targetTitleIsA.isDefinedAt((workWithTitleA, List(workWithTitleB))) shouldBe true
      targetTitleIsA.isDefinedAt((workWithTitleB, List(workWithTitleB))) shouldBe false
    }

    it(
      "is a partial function that is defined only if at least one source satisfies isDefinedForSource") {
      sourceTitleIsA.isDefinedAt(
        (workWithTitleB, List(workWithTitleA, workWithTitleB))) shouldBe true
      sourceTitleIsA.isDefinedAt((workWithTitleB, List(workWithTitleB))) shouldBe false
    }

    it(
      "when applied, calls its rule method only for those sources satisfying isDefinedForSource") {
      val rule = new PartialRule {
        override val isDefinedForTarget: WorkFilter = _ => true
        override val isDefinedForSource: WorkFilter =
          work => work.data.title.contains("A")

        override def rule(target: UnidentifiedWork,
                          sources: Seq[TransformedBaseWork]): FieldData = {
          sources should contain(workWithTitleA)
          sources should not contain workWithTitleB
          None
        }
      }

      rule((workWithTitleB, List(workWithTitleA, workWithTitleB)))
    }
  }

  // This is here because we are extending ComposedFieldMergeRule
  // to access the private PartialRule trait
  override def merge(
    target: UnidentifiedWork,
    sources: Seq[TransformedBaseWork]): MergeResult[FieldData] =
    ???
}
