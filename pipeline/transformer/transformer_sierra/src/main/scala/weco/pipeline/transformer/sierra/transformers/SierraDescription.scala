package weco.pipeline.transformer.sierra.transformers

import weco.pipeline.transformer.marc_common.logging.LoggingContext
import weco.pipeline.transformer.marc_common.transformers.MarcDescription
import weco.pipeline.transformer.sierra.data.SierraMarcDataConversions

import weco.sierra.models.data.SierraBibData
import weco.sierra.models.identifiers.SierraBibNumber

object SierraDescription
    extends SierraIdentifiedDataTransformer
    with SierraMarcDataConversions {

  type Output = Option[String]

  def apply(bibId: SierraBibNumber, bibData: SierraBibData): Option[String] = {
    implicit val ctx: LoggingContext = LoggingContext(bibId.withCheckDigit)
    MarcDescription(bibData)
  }
}
