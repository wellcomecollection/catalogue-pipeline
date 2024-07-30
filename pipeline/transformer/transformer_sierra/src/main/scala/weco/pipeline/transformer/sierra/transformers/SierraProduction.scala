package weco.pipeline.transformer.sierra.transformers

import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work._
import weco.pipeline.transformer.marc_common.transformers.MarcProduction
import weco.pipeline.transformer.sierra.data.SierraMarcDataConversions
import weco.sierra.models.data.SierraBibData
import weco.sierra.models.identifiers.SierraBibNumber

object SierraProduction
    extends SierraIdentifiedDataTransformer
    with SierraMarcDataConversions {

  type Output = List[ProductionEvent[IdState.Unminted]]

  def apply(
    bibId: SierraBibNumber,
    bibData: SierraBibData
  ): List[ProductionEvent[IdState.Unminted]] =
    MarcProduction(bibData, prefer264Field = true)
}
