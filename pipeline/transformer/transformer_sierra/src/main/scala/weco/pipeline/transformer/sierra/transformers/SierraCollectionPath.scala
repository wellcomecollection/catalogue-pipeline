package weco.pipeline.transformer.sierra.transformers

import grizzled.slf4j.Logging
import weco.catalogue.internal_model.work.CollectionPath
import weco.pipeline.transformer.marc_common.transformers.MarcCollectionPath
import weco.pipeline.transformer.sierra.data.SierraMarcDataConversions
import weco.sierra.models.data.SierraBibData

object SierraCollectionPath extends SierraMarcDataConversions with Logging {

  def apply(bibData: SierraBibData): Option[CollectionPath] = {
    MarcCollectionPath(bibData)
  }
}
