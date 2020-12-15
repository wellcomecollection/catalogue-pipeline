package uk.ac.wellcome.platform.sierra_items_to_dynamo.models

import uk.ac.wellcome.sierra_adapter.model.{
  SierraBibNumber,
  SierraItemNumber,
  SierraItemRecord
}

import java.time.Instant

case class SierraItemLink(
  id: SierraItemNumber,
  bibIds: List[SierraBibNumber],
  unlinkedBibIds: List[SierraBibNumber],
  modifiedDate: Instant
)

case object SierraItemLink {
  def apply(record: SierraItemRecord): SierraItemLink =
    SierraItemLink(
      id = record.id,
      bibIds = record.bibIds,
      unlinkedBibIds = record.unlinkedBibIds,
      modifiedDate = record.modifiedDate
    )
}
