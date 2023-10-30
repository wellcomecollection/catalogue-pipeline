package weco.pipeline.merger.rules

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

import weco.catalogue.internal_model.work.generators.MiroWorkGenerators

import WorkPredicates.WorkPredicate
import ImageDataRule.FlatImageMergeRule

class FlatImageMergeRuleTest
    extends AnyFunSpec
    with Matchers
    with MiroWorkGenerators {

  describe("the flat image merging rule") {
    val testRule = new FlatImageMergeRule {
      override val isDefinedForTarget: WorkPredicate = _ => true
      override val isDefinedForSource: WorkPredicate = _ => true
    }

    it("creates images from every source") {
      val target = sierraDigitalIdentifiedWork()
      val sources = (1 to 5).map(_ => miroIdentifiedWork())
      testRule(target, sources).get should have length 5
    }
  }

}
