package weco.pipeline.transformer.sierra.transformers

import weco.catalogue.internal_model.work._
import weco.pipeline.transformer.marc_common.transformers.MarcNotes
import weco.pipeline.transformer.sierra.data.SierraMarcDataConversions
import weco.sierra.models.data.SierraBibData

object SierraNotes
    extends SierraDataTransformer
    with SierraMarcDataConversions {

  type Output = Seq[Note]
  def apply(bibData: SierraBibData): Seq[Note] = {
    MarcNotes(bibDataToMarcRecord(bibData))
  }
}
