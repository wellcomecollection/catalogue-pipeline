package weco.pipeline.merger

import org.scalatest.GivenWhenThen
import org.scalatest.featurespec.AnyFeatureSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.work.Format
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
    Scenario("One Sierra and multiple Miro works are matched") {
      Given("a Sierra work and 3 Miro works")
      val sierra = sierraPhysicalIdentifiedWork()
      val miro1 = miroIdentifiedWork()
      val miro2 = miroIdentifiedWork()
      val miro3 = miroIdentifiedWork()

      When("the works are merged")
      val outcome = merger.merge(Seq(sierra, miro1, miro2, miro3))

      Then("the Miro works are redirected to the Sierra work")
      outcome.getMerged(miro1) should beRedirectedTo(sierra)
      outcome.getMerged(miro2) should beRedirectedTo(sierra)
      outcome.getMerged(miro3) should beRedirectedTo(sierra)

      And("images are created from the Miro works")
      outcome.imageData should contain(miro1.singleImage)
      outcome.imageData should contain(miro2.singleImage)
      outcome.imageData should contain(miro3.singleImage)

      And("the merged Sierra work's images contain all of the images")
      val mergedImages = outcome.getMerged(sierra).data.imageData
      mergedImages should contain(miro1.singleImage)
      mergedImages should contain(miro2.singleImage)
      mergedImages should contain(miro3.singleImage)
    }

    Scenario("One Sierra and one Miro work are matched") {
      Given("a Sierra work and a Miro work")
      val sierra = sierraPhysicalIdentifiedWork()
      val miro = miroIdentifiedWork()

      When("the works are merged")
      val outcome = merger.merge(Seq(sierra, miro))

      Then("the Miro work is redirected to the Sierra work")
      outcome.getMerged(miro) should beRedirectedTo(sierra)

      And("an image is created from the Miro work")
      outcome.imageData should contain only miro.singleImage

      And("the merged Sierra work contains the image")
      outcome
        .getMerged(sierra)
        .data
        .imageData should contain only miro.singleImage
    }

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
          .mergeCandidates(List(createSierraPairMergeCandidateFor(digitisedVideo)))

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
            List(createSierraPairMergeCandidateFor(workWithPhysicalVideoFormats))
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
  }
}
