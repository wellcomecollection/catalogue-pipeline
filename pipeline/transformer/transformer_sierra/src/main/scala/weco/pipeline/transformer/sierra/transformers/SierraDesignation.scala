package weco.pipeline.transformer.sierra.transformers

import weco.pipeline.transformer.marc_common.transformers.MarcDesignation
import weco.pipeline.transformer.sierra.data.SierraMarcDataConversions
import weco.sierra.models.data.SierraBibData

object SierraDesignation
    extends SierraDataTransformer
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
  override def apply(bibData: SierraBibData): List[String] =
    MarcDesignation(bibData).toList
}
