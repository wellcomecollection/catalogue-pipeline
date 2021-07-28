package weco.pipeline.merger.services

import weco.catalogue.internal_model.work.Work
import weco.pipeline.merger.MergerScenarioTest

// We should never really be adding to this test as it is just there to ensure we don't get TEI works
// through the pipes until they are rich enough for us to want to present them to the public
class TeiOffMergerScenarioTest extends MergerScenarioTest {
  override val merger: PlatformMerger = TeiOffMerger

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

    And("the tei work is missing")
    outcome.isMissing(teiWork) shouldBe true
  }

  Scenario("A Tei work becomes deleted") {
    Given("a Tei")
    val teiWork = teiIdentifiedWork().title("A tei work")

    When("the tei work is merged")
    val outcome = merger.merge(List(teiWork))

    And("the tei work is missing")
    outcome.isMissing(teiWork) shouldBe true
  }
}