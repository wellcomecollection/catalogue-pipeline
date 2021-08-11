package weco.pipeline.sierra_linker.models

import weco.catalogue.source_model.sierra.{
  SierraHoldingsRecord,
  SierraItemRecord,
  SierraOrderRecord
}
import weco.sierra.models.identifiers.SierraBibNumber

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

  def apply(orderRecord: SierraOrderRecord): Link =
    Link(
      bibIds = orderRecord.bibIds,
      unlinkedBibIds = orderRecord.unlinkedBibIds,
      modifiedDate = orderRecord.modifiedDate
    )
}
