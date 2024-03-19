package weco.pipeline.transformer.sierra.transformers

import weco.pipeline.transformer.marc_common.logging.LoggingContext
import weco.pipeline.transformer.marc_common.transformers.MarcDesignation
import weco.pipeline.transformer.sierra.data.SierraMarcDataConversions
import weco.sierra.models.data.SierraBibData
import weco.sierra.models.identifiers.SierraBibNumber

object SierraDesignation
    extends SierraIdentifiedDataTransformer
    with SierraMarcDataConversions {
  override type Output = List[String]

  // We use MARC field "362" subfield ǂa.
  //
  // Notes:
  //  - As of November 2022, we use 362 subfields ǂa and ǂz, but it looks like
  //    subfield ǂz (source of information) might not be useful in the public catalogue.
  //
  // See https://www.loc.gov/marc/bibliographic/bd362.html
  //
  override def apply(
    bibId: SierraBibNumber,
    bibData: SierraBibData
  ): List[String] = {
    implicit val ctx: LoggingContext = LoggingContext(bibId.withCheckDigit)
    MarcDesignation(bibData).toList
  }
}
