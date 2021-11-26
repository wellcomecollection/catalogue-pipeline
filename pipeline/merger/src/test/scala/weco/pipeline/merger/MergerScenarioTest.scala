package weco.pipeline.merger

import org.scalatest.GivenWhenThen
import org.scalatest.featurespec.AnyFeatureSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.identifiers.IdentifierType
import weco.catalogue.internal_model.work.{
  CollectionPath,
  Format,
  InvisibilityReason,
  Work,
  WorkState
}
import weco.catalogue.internal_model.work.generators.SourceWorkGenerators
import weco.pipeline.matcher.generators.MergeCandidateGenerators
import weco.pipeline.merger.fixtures.FeatureTestSugar
import weco.pipeline.merger.services.PlatformMerger

class MergerScenarioTest
    extends AnyFeatureSpec
    with GivenWhenThen
    with Matchers
    with FeatureTestSugar
    with MergeCandidateGenerators
    with SourceWorkGenerators {
  val merger = PlatformMerger

  /*
   * We test field-level behaviour in the rule tests, and have to replicate it
   * in the TeiOnPlatformMergerTest. This obscures the top-level merging behaviour
   * which has led to confusion about intended/desired/actual behaviour.
   *
   * These feature tests are intended to be simple smoke tests of merging
   * behaviour which are most valuable as documentation of our intentions.
   */
  Feature("Top-level merging") {
    Scenario("A Sierra picture or ephemera work and METS work are matched") {
      Given("a Sierra picture or ephemera work and a METS work")
      val sierraPicture = sierraIdentifiedWork()
        .items(List(createIdentifiedPhysicalItem))
        .format(Format.Pictures)
      val sierraEphemera = sierraIdentifiedWork()
        .items(List(createIdentifiedPhysicalItem))
        .format(Format.Ephemera)
      val mets = metsIdentifiedWork()

      When("the works are merged")
      val pictureOutcome = merger.merge(Seq(sierraPicture, mets))
      val ephemeraOutcome = merger.merge(Seq(sierraEphemera, mets))

      Then("the METS work is redirected to the Sierra work")
      pictureOutcome.getMerged(mets) should beRedirectedTo(sierraPicture)
      ephemeraOutcome.getMerged(mets) should beRedirectedTo(sierraEphemera)

      And("an image is created from the METS work")
      pictureOutcome.imageData should contain only mets.singleImage
      ephemeraOutcome.imageData should contain only mets.singleImage

      And("the merged Sierra work contains the image")
      pictureOutcome
        .getMerged(sierraPicture)
        .data
        .imageData should contain only mets.singleImage
      ephemeraOutcome
        .getMerged(sierraEphemera)
        .data
        .imageData should contain only mets.singleImage
    }

    Scenario("An AIDS poster Sierra picture, a METS and a Miro are matched") {
      Given(
        "a Sierra picture with digcode `digaids`, a METS work and a Miro work"
      )
      val sierraDigaidsPicture = sierraIdentifiedWork()
      // Multiple physical items would prevent a Miro redirect in any other case,
      // but we still expect to see it for the digaids works as the Miro item is
      // a known duplicate of the METS item.
        .items(List(createIdentifiedPhysicalItem, createIdentifiedPhysicalItem))
        .format(Format.Pictures)
        .otherIdentifiers(List(createDigcodeIdentifier("digaids")))
      val mets = metsIdentifiedWork()
      val miro = miroIdentifiedWork()

      When("the works are merged")
      val outcome = merger.merge(Seq(sierraDigaidsPicture, mets, miro))

      Then("the METS work and the Miro work are redirected to the Sierra work")
      outcome.getMerged(mets) should beRedirectedTo(sierraDigaidsPicture)
      outcome.getMerged(miro) should beRedirectedTo(sierraDigaidsPicture)

      And("the Sierra work contains only the METS images")
      outcome
        .getMerged(sierraDigaidsPicture)
        .data
        .imageData should contain only mets.singleImage
    }

    Scenario("A physical and a digital Sierra work are matched") {
      Given("a pair of a physical Sierra work and a digital Sierra work")
      val (digitalSierra, physicalSierra) = sierraIdentifiedWorkPair()

      When("the works are merged")
      val outcome = merger.merge(Seq(physicalSierra, digitalSierra))

      Then("the digital work is redirected to the physical work")
      outcome.getMerged(digitalSierra) should beRedirectedTo(physicalSierra)

      And("the physical work contains the digitised work's identifiers")
      val physicalIdentifiers = outcome.getMerged(physicalSierra).identifiers
      physicalIdentifiers should contain allElementsOf digitalSierra.identifiers
    }

    Scenario("Audiovisual Sierra works are not merged") {
      Given("a physical Sierra AV work and its digitised counterpart")
      val digitisedVideo =
        sierraDigitalIdentifiedWork().format(Format.EVideos)

      val physicalVideo =
        sierraPhysicalIdentifiedWork()
          .format(Format.Videos)
          .mergeCandidates(
            List(createSierraPairMergeCandidateFor(digitisedVideo)))

      When("the works are merged")
      val outcome = merger.merge(Seq(physicalVideo, digitisedVideo))

      Then("both original works are preserved")
      outcome.resultWorks should contain theSameElementsAs Seq(
        physicalVideo,
        digitisedVideo
      )
    }

    Scenario("A Calm work and a Sierra work are matched") {
      Given("a Sierra work and a Calm work")
      val sierra = sierraPhysicalIdentifiedWork()
      val calm = calmIdentifiedWork()

      When("the works are merged")
      val outcome = merger.merge(Seq(sierra, calm))

      Then("the Sierra work is redirected to the Calm work")
      outcome.getMerged(sierra) should beRedirectedTo(calm)

      And("the Calm work contains the Sierra item ID")
      val calmItem = outcome.getMerged(calm).data.items.head
      calmItem.id shouldBe sierra.data.items.head.id
    }

    Scenario("A Calm work, a Sierra work, and a Miro work are matched") {
      Given("A Calm work, a Sierra work and a Miro work")
      val sierra = sierraPhysicalIdentifiedWork()
      val calm = calmIdentifiedWork()
      val miro = miroIdentifiedWork()

      When("the works are merged")
      val outcome = merger.merge(Seq(sierra, calm, miro))

      Then("the Sierra work is redirected to the Calm work")
      outcome.getMerged(sierra) should beRedirectedTo(calm)

      And("the Miro work is redirected to the Calm work")
      outcome.getMerged(miro) should beRedirectedTo(calm)

      And("the Calm work contains the Miro location")
      outcome.getMerged(calm).data.items.flatMap(_.locations) should
        contain(miro.data.items.head.locations.head)
      And("the Calm work contains the Miro image")
      outcome.getMerged(calm).data.imageData should contain(miro.singleImage)
    }

    Scenario("A Calm work, a Sierra picture work, and a METS work are matched") {
      Given("A Calm work, a Sierra picture work and a METS work")
      val sierraPicture = sierraIdentifiedWork()
        .items(List(createIdentifiedPhysicalItem))
        .format(Format.Pictures)
      val calm = calmIdentifiedWork()
      val mets = metsIdentifiedWork()

      When("the works are merged")
      val outcome = merger.merge(Seq(sierraPicture, calm, mets))

      Then("the Sierra work is redirected to the Calm work")
      outcome.getMerged(sierraPicture) should beRedirectedTo(calm)

      And("the METS work is redirected to the Calm work")
      outcome.getMerged(mets) should beRedirectedTo(calm)

      And("the Calm work contains the METS location")
      outcome.getMerged(calm).data.items.flatMap(_.locations) should
        contain(mets.data.items.head.locations.head)
      And("the Calm work contains the METS image")
      outcome.getMerged(calm).data.imageData should contain(mets.singleImage)
    }

    Scenario("A Calm work and multiple Miro works are matched") {
      Given("A Calm work and 2 Miro works")
      val calm = calmIdentifiedWork()
      val miro1 = miroIdentifiedWork()
      val miro2 = miroIdentifiedWork()

      When("the works are merged")
      val outcome = merger.merge(Seq(calm, miro1, miro2))

      Then("the Miro works are redirected to the Calm work")
      outcome.getMerged(miro1) should beRedirectedTo(calm)
      outcome.getMerged(miro2) should beRedirectedTo(calm)

      And("the Calm work contains the miro items")
      outcome
        .getMerged(calm)
        .data
        .items should contain allElementsOf miro1.data.items
      outcome
        .getMerged(calm)
        .data
        .items should contain allElementsOf miro2.data.items

      And("the Calm work contains the Miro images")
      outcome.getMerged(calm).data.imageData should contain(miro1.singleImage)
      outcome.getMerged(calm).data.imageData should contain(miro2.singleImage)
    }

    Scenario("A digitised video with Sierra physical records and e-bibs") {
      // This test case is based on a real example of four related works that
      // were being merged incorrectly.  In particular, the METS work (and associated
      // IIIF manifest) was being merged into the physical video formats, not the
      // more detailed e-bib that it should have been attached to.
      //
      // See https://wellcome.slack.com/archives/C3TQSF63C/p1615474389063800
      Given("a Sierra physical record, an e-bib, and a METS work")
      val workWithPhysicalVideoFormats =
        sierraIdentifiedWork()
          .title("A work with physical video formats, e.g. DVD or digibeta")
          .format(Format.Film)
          .items(List(createIdentifiedPhysicalItem))

      val workForEbib =
        sierraIdentifiedWork()
          .title("A work for an e-bib")
          .format(Format.Videos)
          .mergeCandidates(
            List(
              createSierraPairMergeCandidateFor(workWithPhysicalVideoFormats))
          )

      val workForMets =
        identifiedWork(sourceIdentifier = createMetsSourceIdentifier)
          .title("A METS work")
          .mergeCandidates(List(createMetsMergeCandidateFor(workForEbib)))
          .items(List(createDigitalItem))
          .invisible()

      When("the works are merged")
      val sierraWorks = List(workWithPhysicalVideoFormats, workForEbib)
      val works = sierraWorks :+ workForMets
      val outcome = merger.merge(works)

      Then("the METS work is redirected to the Sierra e-bib")
      outcome.getMerged(workForMets) should beRedirectedTo(workForEbib)

      And("the Sierra e-bib gets the items from the METS work")
      outcome.getMerged(workForEbib).data.items shouldBe workForMets.data.items

      And("the Sierra physical work is unaffected")
      outcome.getMerged(workWithPhysicalVideoFormats) shouldBe workWithPhysicalVideoFormats
    }

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

    Scenario("A Tei with internal works and a Calm are merged") {
      Given("a Tei and a Calm record")
      val calmWork =
        calmIdentifiedWork().collectionPath(CollectionPath("a/b/c"))
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
      val outcome = merger.merge(works)

      Then("the Cal work is redirected to the tei")
      outcome.getMerged(calmWork) should beRedirectedTo(teiWork)

      And("the tei work retains its collectionPath")
      outcome
        .getMerged(teiWork)
        .data
        .collectionPath shouldBe teiWork.data.collectionPath
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
      val outcome = merger.merge(List(teiWork))

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
      val internalWork1 =
        teiIdentifiedWork().collectionPath(CollectionPath("id/1"))
      val internalWork2 =
        teiIdentifiedWork().collectionPath(CollectionPath("id/2"))
      val teiWork = teiIdentifiedWork()
        .title("A tei work")
        .internalWorks(List(internalWork1, internalWork2))
        .collectionPath(CollectionPath("id"))

      When("the tei work is merged")
      val outcome = merger.merge(List(teiWork))

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
              createSourceIdentifierWith(
                identifierType = IdentifierType.CalmRefNo),
              createSourceIdentifierWith(
                identifierType = IdentifierType.CalmAltRefNo),
            )
          )
          .items(List(createCalmItem))

      // Merge candidates point to the Calm work through the Calm/Sierra harvest
      val sierraWork =
        sierraIdentifiedWork()
          .otherIdentifiers(
            List(
              createSourceIdentifierWith(
                identifierType = IdentifierType.SierraIdentifier),
              createSourceIdentifierWith(
                identifierType = IdentifierType.WellcomeDigcode),
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
          .invisible(invisibilityReasons =
            List(InvisibilityReason.MetsWorksAreNotVisible))

      When("they are merged together")
      val outcome = merger.merge(List(teiWork, sierraWork, metsWork, calmWork))

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
  }

  private def updateInternalWork(
    internalWork: Work.Visible[WorkState.Identified],
    teiWork: Work.Visible[WorkState.Identified]) =
    internalWork
      .copy(version = teiWork.version)
      .mapState(state =>
        state.copy(sourceModifiedTime = teiWork.state.sourceModifiedTime))
}
