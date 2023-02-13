package weco.catalogue.internal_model.work

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.locations.AccessStatus.LicensedResources
import weco.catalogue.internal_model.locations.{AccessStatus, LocationType}
import weco.catalogue.internal_model.work.generators.{
  ItemsGenerators,
  WorkGenerators
}

class AvailabilityTest
    extends AnyFunSpec
    with Matchers
    with WorkGenerators
    with ItemsGenerators {
  describe("Availabilities.forWorkData") {
    it(
      "adds Availability.Online if there is a digital location with an Open, OpenWithAdvisory or LicensedResources access status"
    ) {
      val openWork = denormalisedWork().items(
        List(createDigitalItemWith(accessStatus = AccessStatus.Open))
      )
      val openWithAdvisoryWork = denormalisedWork().items(
        List(
          createDigitalItemWith(accessStatus = AccessStatus.OpenWithAdvisory)
        )
      )
      val licensedResourcesWork = denormalisedWork().items(
        List(
          createDigitalItemWith(accessStatus = AccessStatus.LicensedResources())
        )
      )
      val availabilities =
        List(openWork, openWithAdvisoryWork, licensedResourcesWork)
          .map(work => Availabilities.forWorkData(work.data))

      every(availabilities) should contain only Availability.Online
    }

    it(
      "adds Availability.ClosedStores if there is an item with a closed stores physical location"
    ) {
      val work = denormalisedWork().items(List(createClosedStoresItem))
      val workAvailabilities = Availabilities.forWorkData(work.data)

      workAvailabilities should contain only Availability.ClosedStores
    }

    it(
      "doesn't add Availability.Online if the only digital location is a related resource"
    ) {
      val items =
        List(
          createDigitalItemWith(
            accessStatus =
              AccessStatus.LicensedResources(LicensedResources.RelatedResource)
          )
        )

      val work = denormalisedWork().items(items)

      Availabilities.forWorkData(work.data) shouldBe empty
    }

    it("does not add an availability if the only location is OnOrder") {
      val work = denormalisedWork()
        .items(
          List(
            createIdentifiedItemWith(
              locations = List(
                createPhysicalLocationWith(
                  locationType = LocationType.OnOrder
                )
              )
            )
          )
        )
      val workAvailabilities = Availabilities.forWorkData(work.data)

      workAvailabilities shouldBe empty
    }

    it("does not add Availability.ClosedStores if the location is offsite") {
      val work = denormalisedWork()
        .items(List(createClosedStoresItem))
        .notes(
          List(
            Note(
              contents = "Available at Churchill Archives Centre",
              noteType = NoteType.TermsOfUse
            )
          )
        )
      val workAvailabilities = Availabilities.forWorkData(work.data)

      workAvailabilities shouldBe empty
    }

    it("adds an availability if there are locations other than OnOrder") {
      val work = denormalisedWork()
        .items(
          List(
            createIdentifiedItemWith(
              locations = List(
                createPhysicalLocationWith(
                  locationType = LocationType.OnOrder
                ),
                createPhysicalLocationWith(
                  locationType = LocationType.OpenShelves
                )
              )
            )
          )
        )
      val workAvailabilities = Availabilities.forWorkData(work.data)

      workAvailabilities should contain only Availability.OpenShelves
    }

    describe("if there is a holdings") {
      it("with no physical location, then no availabilities") {
        val work = denormalisedWork()
          .holdings(
            List(
              Holdings(
                note = Some("A holdings in a mystery place"),
                enumeration = Nil,
                location = None
              )
            )
          )
        val workAvailabilities = Availabilities.forWorkData(work.data)

        workAvailabilities shouldBe empty
      }

      it("with an available physical location, then it adds an availability") {
        val work = denormalisedWork()
          .holdings(
            List(
              Holdings(
                note = Some("A holdings in the closed stores"),
                enumeration = Nil,
                location = Some(
                  createPhysicalLocationWith(
                    locationType = LocationType.ClosedStores
                  )
                )
              )
            )
          )
        val workAvailabilities = Availabilities.forWorkData(work.data)

        workAvailabilities shouldBe Set(Availability.ClosedStores)
      }
    }

    it(
      "adds Availability.Online and an in-library availability if the conditions for both are satisfied"
    ) {
      val work = denormalisedWork().items(
        List(
          createClosedStoresItem,
          createDigitalItemWith(accessStatus = AccessStatus.Open)
        )
      )
      val workAvailabilities = Availabilities.forWorkData(work.data)

      workAvailabilities should contain allOf (Availability.ClosedStores, Availability.Online)
    }

    it("does not add either availability if no conditions are satisfied") {
      val work = denormalisedWork().items(
        List(createDigitalItemWith(accessStatus = AccessStatus.Closed))
      )
      val workAvailabilities = Availabilities.forWorkData(work.data)

      workAvailabilities.size shouldBe 0
    }
  }
}
