package uk.ac.wellcome.platform.sierra_items_to_dynamo.merger

import grizzled.slf4j.Logging
import uk.ac.wellcome.sierra_adapter.model.SierraItemRecord
import weco.catalogue.sierra_adapter.linker.LinkingRecord

object SierraItemRecordMerger extends Logging {
  def mergeItems(existingLink: LinkingRecord,
                 newRecord: SierraItemRecord): Option[LinkingRecord] =
    existingLink.update(newRecord)
}
