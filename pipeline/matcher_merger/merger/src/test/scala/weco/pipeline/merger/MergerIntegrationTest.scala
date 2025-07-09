package weco.pipeline.merger

import org.scalatest.GivenWhenThen
import org.scalatest.featurespec.AnyFeatureSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.identifiers.{IdState, IdentifierType}
//import weco.catalogue.internal_model.identifiers.IdentifierType
import weco.catalogue.internal_model.work.DeletedReason.SuppressedFromSource
import weco.catalogue.internal_model.work.{
  CollectionPath,
  Format,
  InvisibilityReason,
  MergeCandidate
}
import weco.catalogue.internal_model.work.generators.SourceWorkGenerators
import weco.fixtures.TimeAssertions
import weco.pipeline.matcher.generators.MergeCandidateGenerators
import weco.pipeline.merger.fixtures.IntegrationTestHelpers

/** Although the matcher and merger are separate applications, they work
  * together as a single unit.
  *
  * These tests are "feature tests" for the matcher+merger together. They only
  * use the external interfaces, and don't rely on how the two apps talk to each
  * other.
  *
  * They're meant:
  *
  *   - to check the inter-app messaging works correctly
  *   - to act as documentation of our intentions.
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
    withContext {
      implicit context =>
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
    withContext {
      implicit context =>
        Given("a Sierra work and 3 Miro works")
        val miro1 = miroIdentifiedWork()
        val miro2 = miroIdentifiedWork()
        val miro3 = miroIdentifiedWork()
        val sierra = sierraPhysicalIdentifiedWork()
          .mergeCandidates(
            List(miro1, miro2, miro3).map(createMiroSierraMergeCandidateFor)
          )

        When("the works are processed by the matcher/merger")
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
        val mergedWorkImages = context.getMerged(sierra).data.imageData
        mergedWorkImages should contain(miro1.singleImage)
        mergedWorkImages should contain(miro2.singleImage)
        mergedWorkImages should contain(miro3.singleImage)
    }
  }

  Scenario("One Sierra and one Miro work are matched") {
    withContext {
      implicit context =>
        Given("a Sierra work and a Miro work")
        val miro = miroIdentifiedWork()
        val sierra = sierraPhysicalIdentifiedWork()
          .mergeCandidates(List(createMiroSierraMergeCandidateFor(miro)))

        When("the works are processed by the matcher/merger")
        processWorks(sierra, miro)

        Then("the Miro work is redirected to the Sierra work")
        context.getMerged(miro) should beRedirectedTo(sierra)

        And("an image is created from the Miro work")
        context.imageData should contain only miro.singleImage

        And("the merged Sierra work contains the image")
        context
          .getMerged(sierra)
          .data
          .imageData should contain only miro.singleImage
    }
  }

  Scenario("One Sierra and one Ebsco work are matched") {
    withContext {
      implicit context =>
        Given("a Sierra work and a Miro work")
        val (sierra, ebsco) = sierraEbscoIdentifiedWorkPair()

        When("the works are processed by the matcher/merger")
        processWorks(sierra, ebsco)

        Then("the Sierra work is redirected to the Ebsco work")
        context.getMerged(sierra) should beRedirectedTo(ebsco)

        And("the Ebsco work should be unmodified")
        context.getMerged(ebsco).data shouldBe ebsco.data
    }
  }

  Scenario("A Sierra picture and METS work are matched") {
    withContext {
      implicit context =>
        Given("a Sierra picture and a METS work")
        val sierraPicture = sierraIdentifiedWork()
          .items(List(createIdentifiedPhysicalItem))
          .format(Format.Pictures)
        val mets = metsIdentifiedWork()
          .mergeCandidates(List(createMetsMergeCandidateFor(sierraPicture)))

        When("the works are processed by the matcher/merger")
        processWorks(sierraPicture, mets)

        Then("the METS work is redirected to the Sierra work")
        context.getMerged(mets) should beRedirectedTo(sierraPicture)

        And("an image is created from the METS work")
        context.imageData should contain only mets.singleImage

        And("the merged Sierra work contains no images")
        context
          .getMerged(sierraPicture)
          .data
          .imageData shouldBe empty

        And(
          "the merged Sierra work contains the locations from both works"
        )
        context
          .getMerged(sierraPicture)
          .data
          .items
          .flatMap(_.locations) should contain theSameElementsAs (
          sierraPicture.data.items ++
            mets.data.items
        ).flatMap(_.locations)
    }
  }

  Scenario("A Sierra ephemera work and METS work are matched") {
    withContext {
      implicit context =>
        Given("a Sierra ephemera work and a METS work")
        val sierraEphemera = sierraIdentifiedWork()
          .items(List(createIdentifiedPhysicalItem))
          .format(Format.Ephemera)
        val mets = metsIdentifiedWork()
          .mergeCandidates(List(createMetsMergeCandidateFor(sierraEphemera)))

        When("the works are processed by the matcher/merger")
        processWorks(sierraEphemera, mets)

        Then("the METS work is redirected to the Sierra work")
        context.getMerged(mets) should beRedirectedTo(sierraEphemera)

        And("an image is created from the METS work")
        context.imageData should contain only mets.singleImage

        And("the merged Sierra work contains the image")
        context
          .getMerged(sierraEphemera)
          .data
          .imageData shouldBe empty

        And(
          "the merged Sierra work contains the locations from both works"
        )
        context
          .getMerged(sierraEphemera)
          .data
          .items
          .flatMap(_.locations) should contain theSameElementsAs (
          sierraEphemera.data.items ++
            mets.data.items
        ).flatMap(_.locations)
    }
  }

  Scenario("An AIDS poster Sierra picture, a METS and a Miro are matched") {
    withContext {
      implicit context =>
        Given(
          "a Sierra picture with digcode `digaids`, a METS work and a Miro work"
        )
        val miro = miroIdentifiedWork()
        val sierraDigaidsPicture = sierraIdentifiedWork()
          // Multiple physical items would prevent a Miro redirect in any other case,
          // but we still expect to see it for the digaids works as the Miro item is
          // a known duplicate of the METS item.
          .items(
            List(createIdentifiedPhysicalItem, createIdentifiedPhysicalItem)
          )
          .format(Format.Pictures)
          .otherIdentifiers(List(createDigcodeIdentifier("digaids")))
          .mergeCandidates(List(createMiroSierraMergeCandidateFor(miro)))
        val mets = metsIdentifiedWork()
          .mergeCandidates(
            List(createMetsMergeCandidateFor(sierraDigaidsPicture))
          )

        When("the works are processed by the matcher/merger")
        processWorks(sierraDigaidsPicture, mets, miro)

        Then(
          "the METS work and the Miro work are redirected to the Sierra work"
        )
        context.getMerged(mets) should beRedirectedTo(sierraDigaidsPicture)
        context.getMerged(miro) should beRedirectedTo(sierraDigaidsPicture)

        And("the Sierra work contains no images")
        context
          .getMerged(sierraDigaidsPicture)
          .data
          .imageData shouldBe empty
        And(
          "the merged Sierra work contains the locations from both works"
        )
        context
          .getMerged(sierraDigaidsPicture)
          .data
          .items
          .flatMap(_.locations) should contain theSameElementsAs (
          sierraDigaidsPicture.data.items ++
            mets.data.items
        ).flatMap(_.locations)
    }
  }

  Scenario("A physical and a digital Sierra work are matched") {
    withContext {
      implicit context =>
        Given("a pair of a physical Sierra work and a digital Sierra work")
        val (digitalSierra, physicalSierra) = sierraIdentifiedWorkPair()

        When("the works are processed by the matcher/merger")
        processWorks(physicalSierra, digitalSierra)

        Then("the digital work is redirected to the physical work")
        context.getMerged(digitalSierra) should beRedirectedTo(physicalSierra)

        And("the physical work contains the digitised work's identifiers")
        val physicalIdentifiers = context.getMerged(physicalSierra).identifiers
        physicalIdentifiers should contain allElementsOf digitalSierra.identifiers
    }
  }

  Scenario("Audiovisual Sierra works are not merged") {
    withContext {
      implicit context =>
        Given("a physical Sierra AV work and its digitised counterpart")
        val digitisedVideo =
          sierraDigitalIdentifiedWork().format(Format.EVideos)

        val physicalVideo =
          sierraPhysicalIdentifiedWork()
            .format(Format.Videos)
            .mergeCandidates(
              List(createSierraPairMergeCandidateFor(digitisedVideo))
            )

        When("the works are processed by the matcher/merger")
        processWorks(physicalVideo, digitisedVideo)

        Then("both original works remain visible")
        context.getMerged(physicalVideo) should beVisible
        context.getMerged(digitisedVideo) should beVisible
    }
  }

  Scenario("A Calm work and a Sierra work are matched") {
    withContext {
      implicit context =>
        Given("a Sierra work and a Calm work")
        val calm = calmIdentifiedWork()
        val sierra = sierraPhysicalIdentifiedWork()
          .mergeCandidates(List(createCalmMergeCandidateFor(calm)))

        When("the works are processed by the matcher/merger")
        processWorks(sierra, calm)

        Then("the Sierra work is redirected to the Calm work")
        context.getMerged(sierra) should beRedirectedTo(calm)

        And("the Calm work contains the Sierra item ID")
        val calmItem = context.getMerged(calm).data.items.head
        calmItem.id shouldBe sierra.data.items.head.id
    }
  }

  Scenario("A Calm work, a Sierra work, and a Miro work are matched") {
    withContext {
      implicit context =>
        Given("A Calm work, a Sierra work and a Miro work")
        val calm = calmIdentifiedWork()
        val miro = miroIdentifiedWork()
        val sierra = sierraPhysicalIdentifiedWork()
          .mergeCandidates(
            List(
              createMiroSierraMergeCandidateFor(miro),
              createCalmMergeCandidateFor(calm)
            )
          )

        When("the works are processed by the matcher/merger")
        processWorks(sierra, calm, miro)

        Then("the Sierra work is redirected to the Calm work")
        context.getMerged(sierra) should beRedirectedTo(calm)

        And("the Miro work is redirected to the Calm work")
        context.getMerged(miro) should beRedirectedTo(calm)

        And("the Calm work contains the Miro location")
        context.getMerged(calm).data.items.flatMap(_.locations) should
          contain(miro.data.items.head.locations.head)
        And("the Calm work contains the Miro image")
        context.getMerged(calm).data.imageData should contain(miro.singleImage)
    }
  }

  Scenario("A Calm work, a Sierra picture work, and a METS work are matched") {
    withContext {
      implicit context =>
        Given("A Calm work, a Sierra picture work and a METS work")
        val calm = calmIdentifiedWork()
        val sierraPicture = sierraIdentifiedWork()
          .items(List(createIdentifiedPhysicalItem))
          .format(Format.Pictures)
          .mergeCandidates(List(createCalmMergeCandidateFor(calm)))
        val mets = metsIdentifiedWork()
          .mergeCandidates(List(createMetsMergeCandidateFor(sierraPicture)))

        When("the works are processed by the matcher/merger")
        processWorks(sierraPicture, calm, mets)

        Then("the Sierra work is redirected to the Calm work")
        context.getMerged(sierraPicture) should beRedirectedTo(calm)

        And("the METS work is redirected to the Calm work")
        context.getMerged(mets) should beRedirectedTo(calm)

        And("the Calm work contains the METS location")
        context.getMerged(calm).data.items.flatMap(_.locations) should
          contain(mets.data.items.head.locations.head)

        And("the Calm work contains the METS image")
        context.getMerged(calm).data.imageData shouldBe empty
    }
  }

  Scenario("A digitised video with Sierra physical records and e-bibs") {
    withContext {
      implicit context =>
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
                createSierraPairMergeCandidateFor(workWithPhysicalVideoFormats)
              )
            )

        val workForMets =
          identifiedWork(sourceIdentifier = createMetsSourceIdentifier)
            .title("A METS work")
            .mergeCandidates(List(createMetsMergeCandidateFor(workForEbib)))
            .items(List(createDigitalItem))
            .invisible()

        When("the works are processed by the matcher/merger")
        processWorks(workWithPhysicalVideoFormats, workForEbib, workForMets)

        Then("the METS work is redirected to the Sierra e-bib")
        context.getMerged(workForMets) should beRedirectedTo(workForEbib)

        And("the Sierra e-bib gets the items from the METS work")
        context
          .getMerged(workForEbib)
          .data
          .items shouldBe workForMets.data.items

        And("the Sierra physical work is unaffected")
        context
          .getMerged(workWithPhysicalVideoFormats)
          .data shouldBe workWithPhysicalVideoFormats.data
        context
          .getMerged(workWithPhysicalVideoFormats)
          .state should beSimilarTo(workWithPhysicalVideoFormats.state)
    }
  }

  Scenario("A Tei and a Sierra digital and a sierra physical work are merged") {
    withContext {
      implicit context =>
        Given("a Tei, a Sierra physical record and a Sierra digital record")
        val (digitalSierra, physicalSierra) = sierraIdentifiedWorkPair()
        val teiWork = teiIdentifiedWork()
          .mergeCandidates(
            List(createTeiBnumberMergeCandidateFor(physicalSierra))
          )

        When("the works are processed by the matcher/merger")
        processWorks(digitalSierra, physicalSierra, teiWork)

        Then("the Sierra works are redirected to the tei")
        context.getMerged(digitalSierra) should beRedirectedTo(teiWork)
        context.getMerged(physicalSierra) should beRedirectedTo(teiWork)

        And("the tei work has the Sierra works' items")
        context
          .getMerged(teiWork)
          .data
          .items should contain allElementsOf digitalSierra.data.items
        context
          .getMerged(teiWork)
          .data
          .items should contain allElementsOf physicalSierra.data.items

        And("the tei work has the Sierra works' identifiers")
        context
          .getMerged(teiWork)
          .data
          .otherIdentifiers should contain allElementsOf physicalSierra.data.otherIdentifiers
          .filter(_.identifierType == IdentifierType.SierraIdentifier)
        context
          .getMerged(teiWork)
          .data
          .otherIdentifiers should contain allElementsOf digitalSierra.data.otherIdentifiers
          .filter(_.identifierType == IdentifierType.SierraIdentifier)
    }
  }

  Scenario(
    "A Tei with internal works and a Sierra digital and a sierra physical work are merged"
  ) {
    withContext {
      implicit context =>
        Given("a Tei, a Sierra physical record and a Sierra digital record")
        val (digitalSierra, physicalSierra) = sierraIdentifiedWorkPair()
        val firstInternalWork =
          teiIdentifiedWork().collectionPath(CollectionPath("TEI_1"))
        val secondInternalWork =
          teiIdentifiedWork().collectionPath(CollectionPath("TEI_2"))

        val teiWork = teiIdentifiedWork()
          .title("A tei work")
          .collectionPath(CollectionPath("TEI"))
          .internalWorks(List(firstInternalWork, secondInternalWork))
          .mergeCandidates(
            List(createTeiBnumberMergeCandidateFor(physicalSierra))
          )

        When("the works are processed by the matcher/merger")
        processWorks(digitalSierra, physicalSierra, teiWork)

        Then("the Sierra works are redirected to the tei")
        context.getMerged(digitalSierra) should beRedirectedTo(teiWork)
        context.getMerged(physicalSierra) should beRedirectedTo(teiWork)

        And("the tei work has the Sierra works' items")
        context
          .getMerged(teiWork)
          .data
          .items should contain allElementsOf digitalSierra.data.items
        context
          .getMerged(teiWork)
          .data
          .items should contain allElementsOf physicalSierra.data.items

        And("the tei work has the Sierra works' identifiers")
        context
          .getMerged(teiWork)
          .data
          .otherIdentifiers should contain allElementsOf physicalSierra.data.otherIdentifiers
          .filter(_.identifierType == IdentifierType.SierraIdentifier)
        context
          .getMerged(teiWork)
          .data
          .otherIdentifiers should contain allElementsOf digitalSierra.data.otherIdentifiers
          .filter(_.identifierType == IdentifierType.SierraIdentifier)

        And("the internal tei works are returned")
        context.getMerged(firstInternalWork) should beVisible
        context.getMerged(secondInternalWork) should beVisible

        And("the tei internal works contain the sierra item")
        context
          .getMerged(firstInternalWork)
          .data
          .items should contain allElementsOf physicalSierra.data.items
        context
          .getMerged(secondInternalWork)
          .data
          .items should contain allElementsOf physicalSierra.data.items

        And("the tei internal works retain their collectionsPath")
        context
          .getMerged(firstInternalWork)
          .data
          .collectionPath shouldBe firstInternalWork.data.collectionPath
        context
          .getMerged(secondInternalWork)
          .data
          .collectionPath shouldBe secondInternalWork.data.collectionPath
    }
  }

  Scenario("A Tei work passes through unchanged") {
    withContext {
      implicit context =>
        Given("a Tei")
        val internalWork1 = teiIdentifiedWork()
        val internalWork2 = teiIdentifiedWork()
        val teiWork = teiIdentifiedWork()
          .title("A tei work")
          .internalWorks(List(internalWork1, internalWork2))

        When("the tei work is merged")
        processWork(teiWork)

        Then("the tei work should be a TEI work")
        val mergedWork = context.getMerged(teiWork)
        mergedWork.data shouldBe teiWork.data
        mergedWork.state should beSimilarTo(teiWork.state)

        And("the the tei inner works should be returned")
        Seq(internalWork1, internalWork2).foreach {
          w =>
            val expectedWork = updateInternalWork(w, teiWork)

            context.getMerged(w).data shouldBe expectedWork.data
            context.getMerged(w).state should beSimilarTo(expectedWork.state)
        }
    }
  }

  Scenario(
    "CollectionPath is prepended to internal tei works if the work is not merged"
  ) {
    withContext {
      implicit context =>
        Given("a Tei")
        val internalWork1 =
          teiIdentifiedWork().collectionPath(CollectionPath("id/1"))
        val internalWork2 =
          teiIdentifiedWork().collectionPath(CollectionPath("id/2"))
        val teiWork = teiIdentifiedWork()
          .title("A tei work")
          .internalWorks(List(internalWork1, internalWork2))
          .collectionPath(CollectionPath("id"))

        When("the work is processed by the matcher/merger")
        processWork(teiWork)

        Then("the tei work should be a TEI work")
        val expectedInternalWork1 = updateInternalWork(internalWork1, teiWork)
          .collectionPath(CollectionPath("id/1"))
        val expectedInternalWork2 = updateInternalWork(internalWork2, teiWork)
          .collectionPath(CollectionPath("id/2"))

        context
          .getMerged(internalWork1)
          .data shouldBe expectedInternalWork1.data
        context.getMerged(internalWork1).state should beSimilarTo(
          expectedInternalWork1.state
        )

        context
          .getMerged(internalWork2)
          .data shouldBe expectedInternalWork2.data
        context.getMerged(internalWork2).state should beSimilarTo(
          expectedInternalWork2.state
        )

        val expectedTeiWork = teiWork.internalWorks(
          List(expectedInternalWork1, expectedInternalWork2)
        )
        context.getMerged(teiWork).data shouldBe expectedTeiWork.data
        context.getMerged(teiWork).state should beSimilarTo(
          expectedTeiWork.state
        )
    }
  }

  Scenario("A TEI work, a Calm work, a Sierra work and a METS work") {
    withContext {
      implicit context =>
        Given("four works")
        val calmWork =
          calmIdentifiedWork()
            .otherIdentifiers(
              List(
                createSourceIdentifierWith(
                  identifierType = IdentifierType.CalmRefNo
                ),
                createSourceIdentifierWith(
                  identifierType = IdentifierType.CalmAltRefNo
                )
              )
            )
            .items(List(createCalmItem))

        val sierraWork =
          sierraIdentifiedWork()
            .otherIdentifiers(
              List(
                createSourceIdentifierWith(
                  identifierType = IdentifierType.SierraIdentifier
                ),
                createSourceIdentifierWith(
                  identifierType = IdentifierType.WellcomeDigcode
                )
              )
            )
            .items(List(createIdentifiedPhysicalItem))
            .mergeCandidates(List(createCalmMergeCandidateFor(calmWork)))

        val teiWork = teiIdentifiedWork()
          .mergeCandidates(List(createTeiBnumberMergeCandidateFor(sierraWork)))

        val metsWork =
          metsIdentifiedWork()
            .thumbnail(createDigitalLocation)
            .items(List(createDigitalItem))
            .mergeCandidates(List(createMetsMergeCandidateFor(sierraWork)))
            .invisible(invisibilityReasons =
              List(InvisibilityReason.MetsWorksAreNotVisible)
            )

        When("the works are processed by the matcher/merger")
        processWorks(teiWork, sierraWork, metsWork, calmWork)

        Then("Everything should be redirected to the TEI work")
        context.getMerged(sierraWork) should beRedirectedTo(teiWork)
        context.getMerged(metsWork) should beRedirectedTo(teiWork)
        context.getMerged(calmWork) should beRedirectedTo(teiWork)

        And("the TEI work gets all the CALM and Sierra identifiers")
        val teiMergedIdentifiers =
          context
            .getMerged(teiWork)
            .data
            .otherIdentifiers

        teiMergedIdentifiers should contain allElementsOf calmWork.data.otherIdentifiers :+ calmWork.state.sourceIdentifier
        teiMergedIdentifiers should contain allElementsOf sierraWork.data.otherIdentifiers :+ sierraWork.state.sourceIdentifier

        And("it has no METS identifier")
        teiMergedIdentifiers.filter(
          _.identifierType == IdentifierType.METS
        ) shouldBe empty

        And("it only has two items (one physical, one digital)")
        val teiItems =
          context
            .getMerged(teiWork)
            .data
            .items

        teiItems should contain allElementsOf sierraWork.data.items
        teiItems should contain allElementsOf metsWork.data.items
        teiItems should contain noElementsOf calmWork.data.items

        And("it gets the METS thumbnail")
        context
          .getMerged(teiWork)
          .data
          .thumbnail shouldBe metsWork.data.thumbnail
    }
  }

  Scenario("Miro, Calm and Sierra but the Miro is deleted") {
    withContext {
      implicit context =>
        // This is a regression test for a bug we saw in the following scenario:
        //
        //      Sierra --> Calm --> Miro
        //
        // where the Miro work was suppressed.
        //
        // Because the matcher graph doesn't include links for suppressed works,
        // this graph would end up with two components (Sierra/Calm) and (Miro).
        // If you later updated the Sierra work, which had no direct link to the Miro
        // work, the matcher wouldn't find the Miro work in the matcher database
        // (because it was in a different component) and the Miro work got blatted.
        //
        // See https://github.com/wellcomecollection/platform/issues/5375
        //
        Given("the works")
        val miro = miroIdentifiedWork()
          .deleted(
            SuppressedFromSource("miro: isClearedForCatalogueApi = false")
          )

        val calm = calmIdentifiedWork()
          .mergeCandidates(List(createCalmMiroMergeCandidateFor(miro)))

        val sierra = sierraIdentifiedWork()
          .mergeCandidates(List(createCalmMergeCandidateFor(calm)))

        When("the works are processed by the matcher/merger")
        // Note: the order is significant here.  The bug only reproduces under
        // certain orderings.
        processWorks(miro, calm, sierra)

        Then("the Sierra work is redirected to the Calm work")
        context.getMerged(sierra) should beRedirectedTo(calm)
    }
  }

  Scenario("Miro, Calm and Sierra but the Miro is missing") {
    withContext {
      implicit context =>
        Given("the works")
        val calm = calmIdentifiedWork()
          .mergeCandidates(
            List(
              MergeCandidate(
                id = IdState.Identified(
                  canonicalId = createCanonicalId,
                  sourceIdentifier = createMiroSourceIdentifier
                ),
                reason = "CALM/Miro work"
              )
            )
          )

        val sierra = sierraIdentifiedWork()
          .mergeCandidates(List(createCalmMergeCandidateFor(calm)))

        When("the works are processed by the matcher/merger")
        // Note: the order is significant here.  The bug only reproduces under
        // certain orderings.
        processWorks(calm, sierra)

        Then("the Sierra work is redirected to the Calm work")
        context.getMerged(sierra) should beRedirectedTo(calm)
        context.getMerged(calm) should beVisible
    }
  }

  Feature("Sierra e-bib/METS work merging is the same, regardless of order") {
    val sierraSuppressedEbib =
      sierraIdentifiedWork()
        .withVersion(version = 1)
        .deleted(SuppressedFromSource("Sierra"))

    val metsIdentifier = sierraSuppressedEbib.sourceIdentifier.copy(
      identifierType = IdentifierType.METS
    )

    val metsWork =
      identifiedWork(sourceIdentifier = metsIdentifier)
        .items(List(createDigitalItem))
        .imageData(List(createMetsImageData.toIdentified))
        .mergeCandidates(
          List(
            createMetsMergeCandidateFor(sierraSuppressedEbib)
          )
        )

    val sierraUnsuppressedEbib =
      identifiedWork(
        canonicalId = sierraSuppressedEbib.state.canonicalId,
        sourceIdentifier = sierraSuppressedEbib.state.sourceIdentifier
      ).withVersion(version = 2)
        .items(
          List(
            createUnidentifiableItemWith(
              locations = List(createDigitalLocation)
            )
          )
        )

    // TODO: These tests are ignored because the stub matcher does not
    // update the matcher graph, so we cannot test the order of work processing.
    ignore("The METS work is sent before the Sierra record is created") {
      withContext {
        implicit context =>
          processWork(metsWork)
          processWork(sierraSuppressedEbib)
          processWork(sierraUnsuppressedEbib)

          context.getMerged(metsWork) should beRedirectedTo(
            sierraUnsuppressedEbib
          )
      }
    }

    // TODO: These tests are ignored because the stub matcher does not
    // update the matcher graph, so we cannot test the order of work processing.
    ignore("The METS work is sent while the e-bib is suppressed") {
      withContext {
        implicit context =>
          processWork(sierraSuppressedEbib)
          processWork(metsWork)
          processWork(sierraUnsuppressedEbib)

          context.getMerged(metsWork) should beRedirectedTo(
            sierraUnsuppressedEbib
          )
      }
    }

    // TODO: These tests are ignored because the stub matcher does not
    // update the matcher graph, so we cannot test the order of work processing.
    ignore("The METS work is sent after the e-bib is unsuppressed") {
      withContext {
        implicit context =>
          processWork(sierraSuppressedEbib)
          processWork(sierraUnsuppressedEbib)
          processWork(metsWork)

          context.getMerged(metsWork) should beRedirectedTo(
            sierraUnsuppressedEbib
          )
      }
    }
  }
}
