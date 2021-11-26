package weco.pipeline.merger

import org.scalatest.GivenWhenThen
import org.scalatest.featurespec.AnyFeatureSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.work.generators.SourceWorkGenerators
import weco.fixtures.TimeAssertions
import weco.pipeline.matcher.generators.MergeCandidateGenerators
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
    with MergeCandidateGenerators
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
      mergedWork.state should beSimilarTo(work.state)
      mergedWork.state.mergedTime should beRecent()
    }
  }

  Scenario("One Sierra and multiple Miro works are matched") {
    withContext { implicit context =>
      Given("a Sierra work and 3 Miro works")
      val miro1 = miroIdentifiedWork()
      val miro2 = miroIdentifiedWork()
      val miro3 = miroIdentifiedWork()
      val sierra = sierraPhysicalIdentifiedWork()
        .mergeCandidates(List(miro1, miro2, miro3).map(createMiroMergeCandidateFor))

      When("the works are merged")
      processWorks(sierra, miro1, miro2, miro3)

      Then("the Miro works are redirected to the Sierra work")
      context.getMerged(miro1) should beRedirectedTo(sierra)
      context.getMerged(miro2) should beRedirectedTo(sierra)
      context.getMerged(miro3) should beRedirectedTo(sierra)

      And("images are created from the Miro works")
      context.imageData should contain(miro1.singleImage)
      context.imageData should contain(miro2.singleImage)
      context.imageData should contain(miro3.singleImage)

      And("the merged Sierra work's images contain all of the images")
      val mergedImages = context.getMerged(sierra).data.imageData
      mergedImages should contain(miro1.singleImage)
      mergedImages should contain(miro2.singleImage)
      mergedImages should contain(miro3.singleImage)
    }
  }
}
