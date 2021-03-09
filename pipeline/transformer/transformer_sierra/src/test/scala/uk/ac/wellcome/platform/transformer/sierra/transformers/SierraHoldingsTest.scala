package uk.ac.wellcome.platform.transformer.sierra.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.models.work.internal.{Holdings, IdState, Item}
import uk.ac.wellcome.platform.transformer.sierra.source.SierraHoldingsData
import weco.catalogue.sierra_adapter.generators.SierraGenerators
import weco.catalogue.sierra_adapter.models.SierraHoldingsNumber

class SierraHoldingsTest extends AnyFunSpec with Matchers with SierraGenerators {
  it("an empty map becomes an empty list of items and holdings") {
    getItems(holdingsDataMap = Map.empty) shouldBe empty
    getHoldings(holdingsDataMap = Map.empty) shouldBe empty
  }

  private def getItems(holdingsDataMap: Map[SierraHoldingsNumber, SierraHoldingsData]): List[Item[IdState.Unminted]] = {
    val (items, _) = SierraHoldings(createSierraBibNumber, holdingsDataMap)
    items
  }

  private def getHoldings(holdingsDataMap: Map[SierraHoldingsNumber, SierraHoldingsData]): List[Holdings] = {
    val (_, holdings) = SierraHoldings(createSierraBibNumber, holdingsDataMap)
    holdings
  }
}
