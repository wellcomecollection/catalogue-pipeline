package weco.pipeline.merger.rules.tei

import weco.pipeline.merger.rules.{BaseOtherIdentifiersRule, BaseOtherIdentifiersRuleTest}

class TeiOtherIdentifiersRuleTest extends BaseOtherIdentifiersRuleTest {
  override val otherIdentifiersRule: BaseOtherIdentifiersRule = TeiOtherIdentifiersRule
}
