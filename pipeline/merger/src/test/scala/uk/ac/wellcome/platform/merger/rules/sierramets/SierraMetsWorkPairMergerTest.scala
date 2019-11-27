package uk.ac.wellcome.platform.merger.rules.sierramets

import org.scalatest.{FunSpec, Inside, Matchers}
import uk.ac.wellcome.models.work.generators.WorksGenerators
import uk.ac.wellcome.models.work.internal.{Identifiable, IdentifiableRedirect, Item, UnidentifiedRedirectedWork, UnidentifiedWork}
import uk.ac.wellcome.platform.merger.model.MergedWork

class SierraMetsWorkPairMergerTest extends FunSpec with WorksGenerators with Matchers with Inside{
  it("merges a Sierra and a Mets work") {
    val physicalWork = createSierraPhysicalWork
    val metsWork = createMetsInvisibleWork

    val result = SierraMetsWorkPairMerger.mergeAndRedirectWorkPair(physicalWork, metsWork)

    val physicalItem: Identifiable[Item] = physicalWork.data.items.head.asInstanceOf[Identifiable[Item]]

    val metsLocation = metsWork.data.items.head.agent.locations.head
    val expectectedItems = List(Identifiable(agent = physicalItem.agent.copy(locations = physicalItem.agent.locations :+ metsLocation), sourceIdentifier = physicalItem.sourceIdentifier))

      inside(result){ case Some(MergedWork(UnidentifiedWork(physicalWork.version, physicalWork.sourceIdentifier, data, physicalWork.ontologyType, physicalWork.identifiedType), redirectedWork)) =>

      data shouldBe physicalWork.data.copy(items = expectectedItems)

      redirectedWork shouldBe UnidentifiedRedirectedWork(
        sourceIdentifier = metsWork.sourceIdentifier,
        version = metsWork.version,
        redirect = IdentifiableRedirect(physicalWork.sourceIdentifier))
    }
  }
}


