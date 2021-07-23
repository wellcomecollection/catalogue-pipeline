package weco.pipeline.merger.rules.tei

import weco.catalogue.internal_model.identifiers.IdentifierType
import weco.pipeline.merger.MergerScenarioTest
import weco.pipeline.merger.services.{Merger, TeiPlatformMerger}

class TeiMergerScenarioTest extends MergerScenarioTest{
  override val merger: Merger = TeiPlatformMerger

  Scenario("A Tei and a Sierra digital and a sierra physical work are merged") {
    Given("a Tei, a Sierra physical record and a Sierra digital record")
    val (digitalSierra, physicalSierra) = sierraIdentifiedWorkPair()
    val teiWork = teiIdentifiedWork().title("A tei work")

    When("the works are merged")
    val sierraWorks = List(digitalSierra, physicalSierra)
    val works = sierraWorks :+ teiWork
    val outcome = merger.merge(works)

    Then("the Sierra works are redirected to the tei")
    outcome.getMerged(digitalSierra) should beRedirectedTo(teiWork)
    outcome.getMerged(physicalSierra) should beRedirectedTo(teiWork)

    And("the tei work has the Sierra works' items")
    outcome
      .getMerged(teiWork)
      .data
      .items should contain allElementsOf digitalSierra.data.items
    outcome
      .getMerged(teiWork)
      .data
      .items should contain allElementsOf physicalSierra.data.items

    And("the tei work has the Sierra works' identifiers")
    outcome
      .getMerged(teiWork)
      .data
      .otherIdentifiers should contain allElementsOf physicalSierra.data.otherIdentifiers
      .filter(_.identifierType == IdentifierType.SierraIdentifier)
    outcome
      .getMerged(teiWork)
      .data
      .otherIdentifiers should contain allElementsOf digitalSierra.data.otherIdentifiers
      .filter(_.identifierType == IdentifierType.SierraIdentifier)
  }
}
