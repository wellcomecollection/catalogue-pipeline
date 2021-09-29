package weco.pipeline.merger.services

import org.scalatest.GivenWhenThen
import org.scalatest.featurespec.AnyFeatureSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.identifiers.{CanonicalId, IdState, IdentifierType, SourceIdentifier}
import weco.catalogue.internal_model.work.{CollectionPath, InvisibilityReason, Item, MergeCandidate, Work, WorkState}
import weco.catalogue.internal_model.work.generators.SourceWorkGenerators
import weco.pipeline.merger.fixtures.FeatureTestSugar

// We'll eventually fold these tests into the base MergerScenarioTest
// once the TEI works are rich enough for the public
class TeiOnMergerScenarioTest
    extends AnyFeatureSpec
    with GivenWhenThen
    with Matchers
    with FeatureTestSugar
    with SourceWorkGenerators {
  val merger = MergerManager.teiOnMergerManager

  Scenario("A Tei and a Sierra digital and a sierra physical work are merged") {
    Given("a Tei, a Sierra physical record and a Sierra digital record")
    val (digitalSierra, physicalSierra) = sierraIdentifiedWorkPair()
    val teiWork = teiIdentifiedWork().title("A tei work")

    When("the works are merged")
    val sierraWorks = List(digitalSierra, physicalSierra)
    val works = sierraWorks :+ teiWork
    val outcome = merger.applyMerge(works.map(Some(_)))

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

  Scenario("A Tei with internal works and a Calm are merged") {
    Given("a Tei and a Calm record")
    val calmWork = calmIdentifiedWork().collectionPath(CollectionPath("a/b/c"))
    val firstInternalWork =
      teiIdentifiedWork().collectionPath(CollectionPath("1"))
    val secondInternalWork =
      teiIdentifiedWork().collectionPath(CollectionPath("2"))

    val teiWork = teiIdentifiedWork()
      .collectionPath(CollectionPath("tei_id"))
      .title("A tei work")
      .internalWorks(List(firstInternalWork, secondInternalWork))

    When("the works are merged")

    val works = List(calmWork, teiWork)
    val outcome = merger.applyMerge(works.map(Some(_)))

    Then("the Cal work is redirected to the tei")
    outcome.getMerged(calmWork) should beRedirectedTo(teiWork)

    And("the tei work has the Calm work collectionPath")
    outcome
      .getMerged(teiWork)
      .data
      .collectionPath shouldBe calmWork.data.collectionPath

    And("the tei inner works have the calm collectionPath prepended")
    outcome
      .getMerged(firstInternalWork)
      .data
      .collectionPath shouldBe Some(CollectionPath(
      s"${calmWork.data.collectionPath.get.path}/${firstInternalWork.data.collectionPath.get.path}"))
    outcome
      .getMerged(secondInternalWork)
      .data
      .collectionPath shouldBe Some(CollectionPath(
      s"${calmWork.data.collectionPath.get.path}/${secondInternalWork.data.collectionPath.get.path}"))
  }

  Scenario(
    "A Tei with internal works and a Sierra digital and a sierra physical work are merged") {
    Given("a Tei, a Sierra physical record and a Sierra digital record")
    val (digitalSierra, physicalSierra) = sierraIdentifiedWorkPair()
    val firstInternalWork =
      teiIdentifiedWork().collectionPath(CollectionPath("1"))
    val secondInternalWork =
      teiIdentifiedWork().collectionPath(CollectionPath("2"))

    val teiWork = teiIdentifiedWork()
      .title("A tei work")
      .internalWorks(List(firstInternalWork, secondInternalWork))

    When("the works are merged")
    val sierraWorks = List(digitalSierra, physicalSierra)
    val works = sierraWorks :+ teiWork
    val outcome = merger.applyMerge(works.map(Some(_)))

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

    And("the internal tei works are returned")
    outcome.resultWorks contains firstInternalWork
    outcome.resultWorks contains secondInternalWork
    outcome.getMerged(firstInternalWork) shouldNot beRedirectedTo(teiWork)
    outcome.getMerged(secondInternalWork) shouldNot beRedirectedTo(teiWork)

    And("the tei internal works contain the sierra item")
    outcome
      .getMerged(firstInternalWork)
      .data
      .items should contain allElementsOf physicalSierra.data.items
    outcome
      .getMerged(secondInternalWork)
      .data
      .items should contain allElementsOf physicalSierra.data.items

    And("the tei internal works retain their collectionsPath")
    outcome
      .getMerged(firstInternalWork)
      .data
      .collectionPath shouldBe firstInternalWork.data.collectionPath
    outcome
      .getMerged(secondInternalWork)
      .data
      .collectionPath shouldBe secondInternalWork.data.collectionPath
  }

  Scenario("A Tei work passes through unchanged") {
    Given("a Tei")
    val internalWork1 = teiIdentifiedWork()
    val internalWork2 = teiIdentifiedWork()
    val teiWork = teiIdentifiedWork()
      .title("A tei work")
      .internalWorks(List(internalWork1, internalWork2))

    When("the tei work is merged")
    val outcome = merger.applyMerge(List(Some(teiWork)))

    Then("the tei work should be a TEI work")
    outcome.getMerged(teiWork) shouldBe teiWork

    And("the the tei inner works should be returned")
    outcome.getMerged(internalWork1) shouldBe updateInternalWork(
      internalWork1,
      teiWork)
    outcome.getMerged(internalWork2) shouldBe updateInternalWork(
      internalWork2,
      teiWork)
  }

  Scenario(
    "CollectionPath is prepended to internal tei works if the work is not merged") {
    Given("a Tei")
    val internalWork1 = teiIdentifiedWork().collectionPath(CollectionPath("1"))
    val internalWork2 = teiIdentifiedWork().collectionPath(CollectionPath("2"))
    val teiWork = teiIdentifiedWork()
      .title("A tei work")
      .internalWorks(List(internalWork1, internalWork2))
      .collectionPath(CollectionPath("id"))

    When("the tei work is merged")
    val outcome = merger.applyMerge(List(Some(teiWork)))

    Then("the tei work should be a TEI work")
    val expectedInternalWork1 = updateInternalWork(internalWork1, teiWork)
      .collectionPath(CollectionPath("id/1"))
    val expectedInternalWork2 = updateInternalWork(internalWork2, teiWork)
      .collectionPath(CollectionPath("id/2"))

    outcome.getMerged(internalWork1) shouldBe expectedInternalWork1
    outcome.getMerged(internalWork2) shouldBe expectedInternalWork2
    outcome.getMerged(teiWork) shouldBe teiWork.internalWorks(
      List(expectedInternalWork1, expectedInternalWork2))
  }

  Scenario("A TEI work, a Calm work, a Sierra work and a METS work") {
    Given("four works")
    val calmWork =
      calmIdentifiedWork()
        .otherIdentifiers(
          List(
            createSourceIdentifierWith(identifierType = IdentifierType.CalmRefNo),
            createSourceIdentifierWith(identifierType = IdentifierType.CalmAltRefNo),
          )
        )
        .items(List(createCalmItem))

    // Merge candidates point to the Calm work through the Calm/Sierra harvest
    val sierraWork =
      sierraIdentifiedWork()
        .otherIdentifiers(
          List(
            createSourceIdentifierWith(identifierType = IdentifierType.SierraIdentifier),
            createSourceIdentifierWith(identifierType = IdentifierType.WellcomeDigcode),
          )
        )
        .items(List(createIdentifiedPhysicalItem))

    // Merge candidates point to the Sierra bib
    val teiWork = teiIdentifiedWork()

    // Merge candidates point to the Sierra e-bib
    val metsWork =
      metsIdentifiedWork()
        .thumbnail(createDigitalLocation)
        .items(List(createDigitalItem))
        .invisible(invisibilityReasons = List(InvisibilityReason.MetsWorksAreNotVisible))

    When("they are merged together")
    val outcome = merger.applyMerge(List(teiWork, sierraWork, metsWork, calmWork).map(Some(_)))

    outcome.getMerged(sierraWork) should beRedirectedTo(teiWork)
    outcome.getMerged(metsWork) should beRedirectedTo(teiWork)
    outcome.getMerged(calmWork) should beRedirectedTo(teiWork)

    Then("the TEI work gets all the CALM and Sierra identifiers")
    val teiMergedIdentifiers =
      outcome
        .getMerged(teiWork)
        .data
        .otherIdentifiers

    teiMergedIdentifiers should contain allElementsOf calmWork.data.otherIdentifiers :+ calmWork.state.sourceIdentifier
    teiMergedIdentifiers should contain allElementsOf sierraWork.data.otherIdentifiers :+ sierraWork.state.sourceIdentifier

    And("it has no METS identifier")
    teiMergedIdentifiers.filter(_.identifierType == IdentifierType.METS) shouldBe empty

    And("it only has two items (one physical, one digital)")
    val teiItems =
      outcome
        .getMerged(teiWork)
        .data
        .items

    teiItems should contain allElementsOf sierraWork.data.items
    teiItems should contain allElementsOf metsWork.data.items
    teiItems should contain noElementsOf calmWork.data.items

    And("it gets the METS thumbnail")
    outcome.getMerged(teiWork).data.thumbnail shouldBe metsWork.data.thumbnail
  }

  private def updateInternalWork(
    internalWork: Work.Visible[WorkState.Identified],
    teiWork: Work.Visible[WorkState.Identified]) =
    internalWork
      .copy(version = teiWork.version)
      .mapState(state =>
        state.copy(sourceModifiedTime = teiWork.state.sourceModifiedTime))
}
