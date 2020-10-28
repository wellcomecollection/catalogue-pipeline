package uk.ac.wellcome.platform.merger

import org.scalatest.GivenWhenThen
import org.scalatest.featurespec.AnyFeatureSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.models.work.generators.{
  MetsWorkGenerators,
  MiroWorkGenerators,
  SierraWorkGenerators
}
import uk.ac.wellcome.models.work.internal.Format
import uk.ac.wellcome.platform.merger.fixtures.FeatureTestSugar
import uk.ac.wellcome.platform.merger.services.PlatformMerger

class MergerFeatureTest
    extends AnyFeatureSpec
    with GivenWhenThen
    with Matchers
    with FeatureTestSugar
    with SierraWorkGenerators
    with MiroWorkGenerators
    with MetsWorkGenerators {
  val merger = PlatformMerger

  /*
   * We test field-level behaviour in the rule tests, and have to replicate it
   * in the PlatformMergerTest. This obscures the top-level merging behaviour
   * which has led to confusion about intended/desired/actual behaviour.
   *
   * These feature tests are intended to be simple smoke tests of merging
   * behaviour which are most valuable as documentation of our intentions.
   */
  Feature("Top-level merging") {
    Scenario("One Sierra and multiple Miro works are matched") {
      Given("a Sierra work and 3 Miro works")
      val sierra = sierraPhysicalSourceWork()
      val miro1 = miroSourceWork()
      val miro2 = miroSourceWork()
      val miro3 = miroSourceWork()

      When("the works are merged")
      val outcome = merger.merge(Seq(sierra, miro1, miro2, miro3))

      Then("the Miro works are redirected to the Sierra work")
      outcome.getMerged(miro1) should beRedirectedTo(sierra)
      outcome.getMerged(miro2) should beRedirectedTo(sierra)
      outcome.getMerged(miro3) should beRedirectedTo(sierra)

      And("images are created from the Miro works")
      outcome.images should contain(miro1.singleImage)
      outcome.images should contain(miro2.singleImage)
      outcome.images should contain(miro3.singleImage)

      And("the merged Sierra work's images contain all of the images")
      val mergedImages = outcome.getMerged(sierra).data.images
      mergedImages should contain(miro1.singleImage)
      mergedImages should contain(miro2.singleImage)
      mergedImages should contain(miro3.singleImage)
    }

    Scenario("One Sierra and one Miro work are matched") {
      Given("a Sierra work and a Miro work")
      val sierra = sierraPhysicalSourceWork()
      val miro = miroSourceWork()

      When("the works are merged")
      val outcome = merger.merge(Seq(sierra, miro))

      Then("the Miro work is redirected to the Sierra work")
      outcome.getMerged(miro) should beRedirectedTo(sierra)

      And("an image is created from the Miro work")
      outcome.images should contain only miro.singleImage

      And("the merged Sierra work contains the image")
      outcome.getMerged(sierra).data.images should contain only miro.singleImage
    }

    Scenario("A Sierra picture work and METS work are matched") {
      Given("a Sierra picture work and a METS work")
      val sierraPicture = sierraSourceWork()
        .items(List(createPhysicalItem))
        .format(Format.Pictures)
      val mets = metsSourceWork()

      When("the works are merged")
      val outcome = merger.merge(Seq(sierraPicture, mets))

      Then("the METS work is redirected to the Sierra work")
      outcome.getMerged(mets) should beRedirectedTo(sierraPicture)

      And("an image is created from the METS work")
      outcome.images should contain only mets.singleImage

      And("the merged Sierra work contains the image")
      outcome
        .getMerged(sierraPicture)
        .data
        .images should contain only mets.singleImage
    }

    Scenario("A physical and a digital Sierra work are matched") {
      Given("a pair of a physical Sierra work and a digital Sierra work")
      val (digitalSierra, physicalSierra) = sierraSourceWorkPair()

      When("the works are merged")
      val outcome = merger.merge(Seq(physicalSierra, digitalSierra))

      Then("the digital work is redirected to the physical work")
      outcome.getMerged(digitalSierra) should beRedirectedTo(physicalSierra)

      And("the physical work contains the digitised work's identifiers")
      val physicalIdentifiers = outcome.getMerged(physicalSierra).identifiers
      physicalIdentifiers should contain allElementsOf digitalSierra.identifiers
    }

    Scenario("A Calm work and a Sierra work are matched") {
      Given("a Sierra work and a Calm work")
      val sierra = sierraPhysicalSourceWork()
      val calm = sourceWork(sourceIdentifier = createCalmSourceIdentifier)
        .items(List(createCalmItem))

      When("the works are merged")
      val outcome = merger.merge(Seq(sierra, calm))

      Then("the Sierra work is redirected to the Calm work")
      outcome.getMerged(sierra) should beRedirectedTo(calm)

      And("the Calm work contains the Sierra item ID")
      val calmItem = outcome.getMerged(calm).data.items.head
      calmItem.id shouldBe sierra.data.items.head.id
    }
  }
}
