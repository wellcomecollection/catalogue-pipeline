package uk.ac.wellcome.models.work.internal

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.models.work.generators.{ItemsGenerators, WorkGenerators}

class AvailabilityTest
    extends AnyFunSpec
    with Matchers
    with WorkGenerators
    with ItemsGenerators {
  describe("Availabilities.forWorkData") {
    it(
      "adds Availability.Online if there is a digital location with an Open, OpenWithAdvisory or LicensedResources access status") {
      val openWork = denormalisedWork().items(
        List(createDigitalItemWith(accessStatus = AccessStatus.Open)))
      val openWithAdvisoryWork = denormalisedWork().items(
        List(
          createDigitalItemWith(accessStatus = AccessStatus.OpenWithAdvisory)))
      val licensedResourcesWork = denormalisedWork().items(
        List(
          createDigitalItemWith(accessStatus = AccessStatus.LicensedResources)))
      val availabilities =
        List(openWork, openWithAdvisoryWork, licensedResourcesWork)
          .map(work => Availabilities.forWorkData(work.data))

      every(availabilities) should contain only Availability.Online
    }

    it("adds Availability.InLibrary if there is a physical location") {
      val work = denormalisedWork().items(List(createIdentifiedPhysicalItem))
      val workAvailabilities = Availabilities.forWorkData(work.data)

      workAvailabilities should contain only Availability.InLibrary
    }

    it(
      "adds Availability.Online and Availability.InLibrary if the conditions for both are satisfied") {
      val work = denormalisedWork().items(
        List(
          createIdentifiedPhysicalItem,
          createDigitalItemWith(accessStatus = AccessStatus.Open)))
      val workAvailabilities = Availabilities.forWorkData(work.data)

      workAvailabilities should contain allOf (Availability.InLibrary, Availability.Online)
    }

    it("does not add either availability if no conditions are satisfied") {
      val work = denormalisedWork().items(
        List(createDigitalItemWith(accessStatus = AccessStatus.Closed)))
      val workAvailabilities = Availabilities.forWorkData(work.data)

      workAvailabilities.size shouldBe 0
    }
  }
}
