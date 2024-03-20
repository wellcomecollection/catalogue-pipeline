package weco.pipeline.transformer.sierra.transformers

import weco.pipeline.transformer.marc_common.transformers.MarcCurrentFrequency
import weco.pipeline.transformer.sierra.data.SierraMarcDataConversions
import weco.sierra.models.data.SierraBibData

object SierraCurrentFrequency
    extends SierraDataTransformer
    with SierraMarcDataConversions {
  override type Output = Option[String]

  override def apply(bibData: SierraBibData): Option[String] =
    MarcCurrentFrequency(bibData)
}
