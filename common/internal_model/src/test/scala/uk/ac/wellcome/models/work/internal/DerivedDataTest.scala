package uk.ac.wellcome.models.work.internal

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import uk.ac.wellcome.models.work.generators.{ImageGenerators, WorkGenerators}

class DerivedDataTest
    extends AnyFunSpec
    with Matchers
    with WorkGenerators
    with ImageGenerators
    with TableDrivenPropertyChecks {

  describe("DerivedWorkData") {
    describe("availableOnline") {
      it("is true if there's a digital location on an item") {
        val work = denormalisedWork().items(
          List(createDigitalItem, createIdentifiedPhysicalItem))
        val derivedWorkData = DerivedWorkData(work.data)

        println(work.data.items)

        derivedWorkData.availableOnline shouldBe true
      }

      it("is true if the digital location isn't on the first item") {
        val work = denormalisedWork().items(
          List(createIdentifiedPhysicalItem, createDigitalItem, createIdentifiedPhysicalItem))
        val derivedWorkData = DerivedWorkData(work.data)

        derivedWorkData.availableOnline shouldBe true
      }

      it("is false if there isn't a digital location on any items") {
        val work = denormalisedWork().items(List(createIdentifiedPhysicalItem))
        val derivedWorkData = DerivedWorkData(work.data)

        derivedWorkData.availableOnline shouldBe false
      }

      it("is false if there aren't any items") {
        val work = denormalisedWork().items(Nil)
        val derivedWorkData = DerivedWorkData(work.data)

        derivedWorkData.availableOnline shouldBe false
      }

      val unavailableStatuses = Table(
        "condition",
        AccessStatus.Closed,
        AccessStatus.Unavailable,
      )

      it("is false if all the items have an unavailable access status") {
        unavailableStatuses.forEvery { status =>
          val work =
            denormalisedWork()
              .items(List(
                createDigitalItemWith(
                  locations = List(createDigitalLocationWith(
                    accessConditions = List(
                      AccessCondition(status = Some(status))
                    )
                  ))
                ),
                createDigitalItemWith(
                  locations = List(createDigitalLocationWith(
                    accessConditions = List(
                      AccessCondition(status = Some(status))
                    )
                  ))
                ),
                createIdentifiedPhysicalItem
              ))

          val derivedWorkData = DerivedWorkData(work.data)

          derivedWorkData.availableOnline shouldBe false
        }
      }

      it("is true if not every item has an unavailable status") {
        unavailableStatuses.forEvery { status =>
          val work =
            denormalisedWork()
              .items(List(
                createDigitalItemWith(
                  locations = List(createDigitalLocationWith(
                    accessConditions = List(
                      AccessCondition(status = Some(status)),
                    )
                  ))
                ),
                createDigitalItemWith(
                  locations = List(createDigitalLocationWith(
                    accessConditions = List(
                      AccessCondition(status = None)
                    )
                  ))
                ),
                createIdentifiedPhysicalItem
              ))

          val derivedWorkData = DerivedWorkData(work.data)

          derivedWorkData.availableOnline shouldBe true
        }
      }

      it("is true if not every access condition has an unavailable status") {
        unavailableStatuses.forEvery { status =>
          val work =
            denormalisedWork()
              .items(List(
                createDigitalItemWith(
                  locations = List(createDigitalLocationWith(
                    accessConditions = List(
                      AccessCondition(status = Some(status)),
                      AccessCondition(status = Some(AccessStatus.Open))
                    )
                  ))
                ),
                createDigitalItemWith(
                  locations = List(createDigitalLocationWith(
                    accessConditions = List(
                      AccessCondition(status = Some(status)),
                    )
                  ))
                ),
                createIdentifiedPhysicalItem
              ))

          val derivedWorkData = DerivedWorkData(work.data)

          derivedWorkData.availableOnline shouldBe true
        }
      }
    }

    it("derives contributorAgents from a heterogenous list of contributors") {
      val agents = List(
        Agent("0048146"),
        Organisation("PKK"),
        Person("Salt Bae"),
        Meeting("Brunch, 18th Jan 2021")
      )
      val work =
        denormalisedWork().contributors(agents.map(Contributor(_, roles = Nil)))
      val derivedWorkData = DerivedWorkData(work.data)

      derivedWorkData.contributorAgents shouldBe List(
        "Agent:0048146",
        "Organisation:PKK",
        "Person:Salt Bae",
        "Meeting:Brunch, 18th Jan 2021"
      )
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
