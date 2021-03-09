package uk.ac.wellcome.platform.transformer.sierra.transformers

import uk.ac.wellcome.models.work.internal.{Holdings, IdState, Item}
import uk.ac.wellcome.platform.transformer.sierra.source.SierraHoldingsData
import weco.catalogue.sierra_adapter.models.{SierraBibNumber, SierraHoldingsNumber}

object SierraHoldings {
  type Output = (List[Item[IdState.Unminted]], List[Holdings])

  def apply(id: SierraBibNumber, holdingsDataMap: Map[SierraHoldingsNumber, SierraHoldingsData]): Output =
    (List(), List())
}
