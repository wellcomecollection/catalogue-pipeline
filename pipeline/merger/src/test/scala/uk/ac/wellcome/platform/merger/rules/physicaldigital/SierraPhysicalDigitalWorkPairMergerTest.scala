package uk.ac.wellcome.platform.merger.rules.physicaldigital

import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.models.work.generators.WorksGenerators
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.merger.model.MergedWork

class SierraPhysicalDigitalWorkPairMergerTest
    extends FunSpec
    with WorksGenerators
    with Matchers {
  val workPairMerger = new SierraPhysicalDigitalWorkPairMerger {}
  it("merges a Sierra physical and a Sierra digital work") {
    val physicalWork = createSierraPhysicalWork
    val digitalWork = createSierraDigitalWork

    val result =
      workPairMerger.mergeAndRedirectWorkPair(physicalWork, digitalWork)

    result shouldBe Some(
      MergedWork(
        expectedMergedWork(physicalWork, digitalWork),
        UnidentifiedRedirectedWork(
          sourceIdentifier = digitalWork.sourceIdentifier,
          version = digitalWork.version,
          redirect = IdentifiableRedirect(physicalWork.sourceIdentifier))
      ))
  }

  it("merges if physical work has >1 items") {
    val workWithTwoPhysicalItems = createSierraWorkWithTwoPhysicalItems
    val digitalWork = createSierraDigitalWork
    workPairMerger.mergeAndRedirectWorkPair(
      workWithTwoPhysicalItems,
      digitalWork) shouldBe Some(
      MergedWork(
        workWithTwoPhysicalItems.withData { data =>
          data.copy(
            otherIdentifiers = workWithTwoPhysicalItems.otherIdentifiers ++ digitalWork.identifiers,
            items = workWithTwoPhysicalItems.data.items :+ digitalWork.data.items.head
          )
        },
        UnidentifiedRedirectedWork(
          sourceIdentifier = digitalWork.sourceIdentifier,
          version = digitalWork.version,
          redirect =
            IdentifiableRedirect(workWithTwoPhysicalItems.sourceIdentifier))
      ))
  }

  it("does not merge if physical work has 0 items") {
    workPairMerger.mergeAndRedirectWorkPair(
      createSierraWorkWithoutItems,
      createSierraDigitalWork) shouldBe None
  }

  it("does not merge if digital work has 0 items") {
    workPairMerger.mergeAndRedirectWorkPair(
      createSierraPhysicalWork,
      createSierraWorkWithoutItems) shouldBe None
  }

  it("does not merge if digital work has >1 items") {
    workPairMerger.mergeAndRedirectWorkPair(
      createSierraPhysicalWork,
      createSierraWorkWithTwoDigitalItems) shouldBe None
  }
  private def createSierraWorkWithTwoDigitalItems =
    createUnidentifiedSierraWorkWith(
      items = List(createDigitalItem, createDigitalItem)
    )
  private def createSierraWorkWithoutItems = createUnidentifiedSierraWorkWith(
    items = List()
  )

  private def expectedMergedWork(physicalWork: UnidentifiedWork,
                                 digitalWork: UnidentifiedWork) = {
    val sierraPhysicalAgent = physicalWork.data.items.head.thing
    val sierraDigitalAgent = digitalWork.data.items.head.thing

    val expectedLocations = sierraPhysicalAgent.locations ++ sierraDigitalAgent.locations

    val expectedItem = physicalWork.data.items.head
      .asInstanceOf[Identifiable[Item]]
      .copy(
        thing = sierraPhysicalAgent.copy(
          locations = expectedLocations
        )
      )
    val expectedOtherIdentifiers = physicalWork.otherIdentifiers ++ digitalWork.identifiers

    physicalWork.withData { data =>
      data.copy(
        otherIdentifiers = expectedOtherIdentifiers,
        items = List(expectedItem)
      )
    }
  }
}
