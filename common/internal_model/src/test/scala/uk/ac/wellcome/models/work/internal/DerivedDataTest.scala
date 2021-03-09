package uk.ac.wellcome.models.work.internal

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.models.work.generators.{ImageGenerators, WorkGenerators}

class DerivedDataTest
    extends AnyFunSpec
    with Matchers
    with WorkGenerators
    with ImageGenerators {

  describe("DerivedWorkData") {
    describe("contributorAgents") {
      it("derives contributorAgents from a heterogenous list of contributors") {
        val agents = List(
          Agent("0048146"),
          Organisation("PKK"),
          Person("Salt Bae"),
          Meeting("Brunch, 18th Jan 2021")
        )
        val work =
          denormalisedWork().contributors(
            agents.map(Contributor(_, roles = Nil)))
        val derivedWorkData = DerivedWorkData(work.data)

        derivedWorkData.contributorAgents shouldBe List(
          "Agent:0048146",
          "Organisation:PKK",
          "Person:Salt Bae",
          "Meeting:Brunch, 18th Jan 2021"
        )
      }
    }
  }

  describe("DerivedImageData") {

    it(
      "sets the thumbnail to the first iiif-image location it finds in locations") {
      val imageLocation = createImageLocation
      val image = createImageDataWith(locations =
        List(createManifestLocation, imageLocation)).toAugmentedImage
      val derivedImageData = DerivedImageData(image)

      derivedImageData.thumbnail shouldBe imageLocation
    }

    it("throws an error if there is no iiif-image location") {
      val image =
        createImageDataWith(locations = List(createManifestLocation)).toAugmentedImage

      assertThrows[RuntimeException] {
        DerivedImageData(image)
      }
    }

    it("constructs a list of contributor agent labels from both source works") {
      val image = createImageData.toAugmentedImageWith(
        parentWork = identifiedWork().contributors(
          List(Contributor(Organisation("Planet Express"), roles = Nil))
        ),
        redirectedWork = Some(
          identifiedWork().contributors(
            List(Contributor(Person("Zaphod Beeblebrox"), roles = Nil))
          )
        )
      )
      val derivedImageData = DerivedImageData(image)

      derivedImageData.sourceContributorAgents should contain allOf (
        "Organisation:Planet Express",
        "Person:Zaphod Beeblebrox"
      )
    }
  }
}
