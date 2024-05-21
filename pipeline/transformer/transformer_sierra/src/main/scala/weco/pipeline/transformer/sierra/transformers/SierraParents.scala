package weco.pipeline.transformer.sierra.transformers

import grizzled.slf4j.Logging
import weco.catalogue.internal_model.work.Relation
import weco.pipeline.transformer.marc_common.transformers.MarcParents
import weco.pipeline.transformer.sierra.data.SierraMarcDataConversions
import weco.sierra.models.data.SierraBibData

object SierraParents extends SierraMarcDataConversions with Logging {
  def apply(bibData: SierraBibData): List[Relation] =
    MarcParents(bibData)
}
