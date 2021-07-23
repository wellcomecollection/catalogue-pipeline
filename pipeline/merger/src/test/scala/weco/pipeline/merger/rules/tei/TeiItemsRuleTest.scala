package weco.pipeline.merger.rules.tei

import weco.catalogue.internal_model.work.{Work, WorkState}
import weco.pipeline.merger.models.FieldMergeResult
import weco.pipeline.merger.rules.{BaseItemsRule, BaseItemsRuleTest}

class TeiItemsRuleTest extends BaseItemsRuleTest {

  val tei: Work.Visible[WorkState.Identified] =
    teiIdentifiedWork()
  it("merges the item from Sierra works tei works") {
    inside(itemsRule.merge(tei, List(multiItemPhysicalSierra))) {
      case FieldMergeResult(items, mergedSources) =>
        items should have size 2
        items.head shouldBe multiItemPhysicalSierra.data.items.head
        mergedSources should be(Seq(multiItemPhysicalSierra))
    }
  }
  override val itemsRule: BaseItemsRule = TeiItemsRule
}
