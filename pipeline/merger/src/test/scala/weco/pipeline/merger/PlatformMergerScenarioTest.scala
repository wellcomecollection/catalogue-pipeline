package weco.pipeline.merger

import weco.catalogue.internal_model.work.Work
import weco.pipeline.merger.services.{Merger, DefaultPlatformMerger}

class PlatformMergerScenarioTest extends MergerScenarioTest{
  override val merger: Merger = DefaultPlatformMerger
  Scenario("A Tei is not merged with a Sierra digital and a sierra physical") {
    Given("a Tei, a Sierra physical record and a Sierra digital record")
    val (digitalSierra, physicalSierra) = sierraIdentifiedWorkPair()
    val teiWork = teiIdentifiedWork().title("A tei work")

    When("the works are merged")
    val sierraWorks = List(digitalSierra, physicalSierra)
    val works = sierraWorks :+ teiWork
    val outcome = merger.merge(works)

    Then("the digital Sierra is redirected to the physical sierra")
    outcome.getMerged(digitalSierra) should beRedirectedTo(physicalSierra)
    outcome.getMerged(physicalSierra) shouldBe a[Work.Visible[_]]

    And("the tei work passes through unchanged")
    outcome
      .getMerged(teiWork) shouldBe teiWork
  }
}
