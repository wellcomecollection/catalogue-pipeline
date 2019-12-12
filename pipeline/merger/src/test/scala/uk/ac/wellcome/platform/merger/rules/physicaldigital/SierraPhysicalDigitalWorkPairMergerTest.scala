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

      val result = workPairMerger.mergeAndRedirectWorkPair(physicalWork, digitalWork)

      result shouldBe Some(MergedWork(
        expectedMergedWork(physicalWork, digitalWork),
        UnidentifiedRedirectedWork(
          sourceIdentifier = digitalWork.sourceIdentifier,
          version = digitalWork.version,
          redirect = IdentifiableRedirect(physicalWork.sourceIdentifier))
      ))
    }

    it("does not merge if physical work has 0 items") {
      workPairMerger.mergeAndRedirectWorkPair(createSierraWorkWithoutItems, createSierraDigitalWork) shouldBe None
    }

    it("does not merge if physical work has >1 items") {
      workPairMerger.mergeAndRedirectWorkPair(createSierraWorkWithTwoPhysicalItems, createSierraDigitalWork) shouldBe None
    }

    it("does not merge if digital work has 0 items") {
      workPairMerger.mergeAndRedirectWorkPair(createSierraPhysicalWork, createSierraWorkWithoutItems) shouldBe None
    }

    it("does not merge if digital work has >1 items") {
      workPairMerger.mergeAndRedirectWorkPair(createSierraPhysicalWork, createSierraWorkWithTwoDigitalItems) shouldBe None
    }

  private def createSierraWorkWithTwoPhysicalItems =
    createUnidentifiedSierraWorkWith(
      items = List(createPhysicalItem, createPhysicalItem)
    )
  private def createSierraWorkWithTwoDigitalItems =
    createUnidentifiedSierraWorkWith(
      items = List(createDigitalItem, createDigitalItem)
    )
  private def createSierraWorkWithoutItems = createUnidentifiedSierraWorkWith(
    items = List()
  )

  private def expectedMergedWork(physicalWork: UnidentifiedWork,
                                 digitalWork: UnidentifiedWork) = {
    val sierraPhysicalAgent = physicalWork.data.items.head.agent
    val sierraDigitalAgent = digitalWork.data.items.head.agent

    val expectedLocations = sierraPhysicalAgent.locations ++ sierraDigitalAgent.locations

    val expectedItem = physicalWork.data.items.head
      .asInstanceOf[Identifiable[Item]]
      .copy(
        agent = sierraPhysicalAgent.copy(
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
