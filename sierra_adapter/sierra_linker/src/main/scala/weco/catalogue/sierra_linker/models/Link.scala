package weco.catalogue.sierra_linker.models

import weco.catalogue.sierra_adapter.models.{
  SierraBibNumber,
  SierraHoldingsRecord,
  SierraItemRecord
}

import java.time.Instant

case class Link(
  bibIds: List[SierraBibNumber],
  unlinkedBibIds: List[SierraBibNumber],
  modifiedDate: Instant
)

case object Link {
  def apply(itemRecord: SierraItemRecord): Link =
    Link(
      bibIds = itemRecord.bibIds,
      unlinkedBibIds = itemRecord.unlinkedBibIds,
      modifiedDate = itemRecord.modifiedDate
    )

  def apply(holdingsRecord: SierraHoldingsRecord): Link =
    Link(
      bibIds = holdingsRecord.bibIds,
      unlinkedBibIds = holdingsRecord.unlinkedBibIds,
      modifiedDate = holdingsRecord.modifiedDate
    )
}
