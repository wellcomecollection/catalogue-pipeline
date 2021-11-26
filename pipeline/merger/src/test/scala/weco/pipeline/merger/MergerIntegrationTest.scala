package weco.pipeline.merger

import org.scalatest.GivenWhenThen
import org.scalatest.featurespec.AnyFeatureSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.work.generators.SourceWorkGenerators
import weco.fixtures.TimeAssertions
import weco.pipeline.merger.fixtures.IntegrationTestHelpers

/** Although the matcher and merger are separate applications, they work together
  * as a single unit.
  *
  * These tests are "feature tests" for the matcher+merger together.  They only
  * use the external interfaces, and don't rely on how the two apps talk to each other.
  *
  * They're meant:
  *
  *    - to check the inter-app messaging works correctly
  *    - to act as documentation of our intentions.
  *
  */
class MergerIntegrationTest
  extends AnyFeatureSpec
    with GivenWhenThen
    with Matchers
    with SourceWorkGenerators
    with TimeAssertions
    with IntegrationTestHelpers {

  Scenario("A single work with nothing linked to it") {
    withContext { implicit context =>
      Given("A single work")
      val work = identifiedWork()

      When("the work is processed by the matcher/merger")
      processWork(work)

      Then("the work is returned")
      val mergedWork = context.getMerged(work)
      mergedWork.data shouldBe work.data
      assertSimilarState(mergedWork, work)
      assertRecent(mergedWork.state.mergedTime)
    }
  }
}
