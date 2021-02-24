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

    describe("availableOnline") {
      it(
        "sets availableOnline = true if there is a digital location on an item") {
        val work = denormalisedWork().items(
          List(createDigitalItem, createIdentifiedPhysicalItem))
        val derivedWorkData = DerivedWorkData(work.data)

        derivedWorkData.availableOnline shouldBe true
      }

      it("sets availableOnline = false if there is no digital location on any items") {
        val work = denormalisedWork().items(List(createIdentifiedPhysicalItem))
        val derivedWorkData = DerivedWorkData(work.data)

        derivedWorkData.availableOnline shouldBe false
      }

      it("handles empty items list") {
        val work = denormalisedWork().items(Nil)
        val derivedWorkData = DerivedWorkData(work.data)

        derivedWorkData.availableOnline shouldBe false
      }
    }

    describe("availabilities") {
      it("adds Availability.Online if there is a digital location with an Open or OpenWithAdvisory access status") {
        val openWork = denormalisedWork().items(
          List(createDigitalItemWith(accessStatus = AccessStatus.Open)))
        val openWithAdvisoryWork = denormalisedWork().items(List(
          createDigitalItemWith(accessStatus = AccessStatus.OpenWithAdvisory)))
        val derivedOpenWorkData = DerivedWorkData(openWork.data)
        val derivedOpenWithAdvisoryWorkData =
          DerivedWorkData(openWithAdvisoryWork.data)

        derivedOpenWorkData.availabilities should contain only Availability.Online
        derivedOpenWithAdvisoryWorkData.availabilities should contain only Availability.Online
      }

      it("adds Availability.InLibrary if there is a physical location") {
        val work = denormalisedWork().items(List(createIdentifiedPhysicalItem))
        val derivedWorkData = DerivedWorkData(work.data)

        derivedWorkData.availabilities should contain only Availability.InLibrary
      }

      it("adds Availability.Online and Availability.InLibrary if the conditions for both are satisfied") {
        val work = denormalisedWork().items(
          List(
            createIdentifiedPhysicalItem,
            createDigitalItemWith(accessStatus = AccessStatus.Open)))
        val derivedWorkData = DerivedWorkData(work.data)

        derivedWorkData.availabilities should contain allOf (Availability.InLibrary, Availability.Online)
      }
    }

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
  }
}
