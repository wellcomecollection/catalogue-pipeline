package weco.pipeline.merger.services

import org.scalatest.GivenWhenThen
import org.scalatest.featurespec.AnyFeatureSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.work.Work
import weco.catalogue.internal_model.work.generators.SourceWorkGenerators
import weco.pipeline.merger.fixtures.FeatureTestSugar

// We should never really be adding to this test as it is just there to ensure we don't get TEI works
// through the pipes until they are rich enough for us to want to present them to the public
class TeiOffMergerScenarioTest
    extends AnyFeatureSpec
    with GivenWhenThen
    with Matchers
    with FeatureTestSugar
    with SourceWorkGenerators {
  val merger = MergerManager.teiOffMergerManager

  Scenario("A Tei is not merged with a Sierra digital and a sierra physical") {
    Given("a Tei, a Sierra physical record and a Sierra digital record")
    val (digitalSierra, physicalSierra) = sierraIdentifiedWorkPair()
    val teiWork = teiIdentifiedWork().title("A tei work")

    When("the works are merged")
    val sierraWorks = List(digitalSierra, physicalSierra)
    val works = sierraWorks :+ teiWork
    val outcome = merger.applyMerge(works.map(Some(_)))

    Then("the digital Sierra is redirected to the physical sierra")
    outcome.getMerged(digitalSierra) should beRedirectedTo(physicalSierra)
    outcome.getMerged(physicalSierra) shouldBe a[Work.Visible[_]]

    And("the tei work is missing")
    outcome.isDeleted(teiWork) shouldBe true
  }
  Scenario("Tei internal works are not returned") {
    Given("a Tei, a Sierra physical record and a Sierra digital record")
    val (digitalSierra, physicalSierra) = sierraIdentifiedWorkPair()
    val firstInternalWork = teiIdentifiedWork()
    val secondInternalWork = teiIdentifiedWork()

    val teiWork = teiIdentifiedWork().title("A tei work").mapState(state => {
      state.copy(internalWorks = List(firstInternalWork, secondInternalWork))
    })
    When("the works are merged")
    val sierraWorks = List(digitalSierra, physicalSierra)
    val works = sierraWorks :+ teiWork
    val outcome = merger.applyMerge(works.map(Some(_)))

    Then("the digital Sierra is redirected to the physical sierra")
    outcome.getMerged(digitalSierra) should beRedirectedTo(physicalSierra)
    outcome.getMerged(physicalSierra) shouldBe a[Work.Visible[_]]

    And("the tei work is missing")
    outcome.isDeleted(teiWork) shouldBe true

    And("the internal tei works are missing")
    outcome.resultWorks.exists(_.sourceIdentifier == firstInternalWork.sourceIdentifier) shouldBe false
    outcome.resultWorks.exists(_.sourceIdentifier == secondInternalWork.sourceIdentifier) shouldBe false
  }

  Scenario("A Tei work becomes deleted") {
    Given("a Tei")
    val teiWork = teiIdentifiedWork().title("A tei work")

    When("the tei work is merged")
    val outcome = merger.applyMerge(List(Some(teiWork)))

    And("the tei work is deleted")
    outcome.isDeleted(teiWork) shouldBe true
  }

  Scenario("A Tei work with a merge candidate not in the index becomes deleted") {
    Given("a Tei")
    val teiWork = teiIdentifiedWork().title("A tei work")
    val otherWork = None
    When("the tei work is merged")
    val outcome = merger.applyMerge(List(Some(teiWork), otherWork))

    And("the tei work is deleted")
    outcome.isDeleted(teiWork) shouldBe true
  }
}
