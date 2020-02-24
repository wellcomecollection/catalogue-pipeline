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
    with ComposedFieldMergeRule
    with WorksGenerators {
  override protected type Field = Option[String]

  val targetTitleIsA = new PartialRule {
    override val isDefinedForTarget: WorkFilter =
      work => work.data.title.contains("A")
    override val isDefinedForSource: WorkFilter = _ => true

    override def rule(target: UnidentifiedWork,
                      sources: Seq[TransformedBaseWork]): Field = None
  }
  val sourceTitleIsA = new PartialRule {
    override val isDefinedForTarget: WorkFilter = _ => true
    override val isDefinedForSource: WorkFilter =
      work => work.data.title.contains("A")

    override def rule(target: UnidentifiedWork,
                      sources: Seq[TransformedBaseWork]): Field = None
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
                          sources: Seq[TransformedBaseWork]): Field = {
          sources should contain(workWithTitleA)
          sources should not contain workWithTitleB
          None
        }
      }

      rule((workWithTitleB, List(workWithTitleA, workWithTitleB)))
    }
  }

  describe("ComposedFieldMergeRule") {
    val titleConcatRule = (toConcat: String) =>
      new PartialRule {
        override val isDefinedForTarget: WorkFilter = _ => true
        override val isDefinedForSource: WorkFilter = _ => true

        override def rule(target: UnidentifiedWork,
                          sources: Seq[TransformedBaseWork]): Field =
          target.data.title.map(_ + toConcat)
    }

    val liftTitleIntoTarget = (target: UnidentifiedWork) =>
      (title: Option[String]) =>
        target withData { data =>
          data.copy(title = title)
    }

    it(
      "can compose partial rules right-to-left when provided with a function to lift field results into new target works") {
      val composedRule = composeRules(liftTitleIntoTarget)(
        titleConcatRule("D"),
        titleConcatRule("C"),
        titleConcatRule("B"))(_, _)
      composedRule(workWithTitleA, List(workWithTitleB)).data.title shouldBe
        Some("ABCD")
    }

    it(
      "does not modify the target or discard the result when the input is out of one of the composed rules' domains") {
      val composedRule = composeRules(liftTitleIntoTarget)(
        titleConcatRule("D"),
        titleConcatRule("C"),
        targetTitleIsA,
        titleConcatRule("B"))(_, _)
      composedRule(workWithTitleA, List(workWithTitleB)).data.title shouldBe
        Some("ABCD")
    }
  }

  // This is here because we are extending ComposedFieldMergeRule
  // to access the private PartialRule trait
  override def merge(target: UnidentifiedWork,
                     sources: Seq[TransformedBaseWork]): MergeResult[Field] =
    ???
}
