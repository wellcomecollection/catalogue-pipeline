package weco.catalogue.sierra_adapter.linker

import uk.ac.wellcome.sierra_adapter.model.{SierraBibNumber, SierraItemRecord}

import java.time.Instant

case class LinkingRecord(
  bibIds: List[SierraBibNumber],
  unlinkedBibIds: List[SierraBibNumber],
  modifiedDate: Instant
)

case object LinkingRecord {
  def apply(record: SierraItemRecord): LinkingRecord =
    LinkingRecord(
      bibIds = record.bibIds,
      unlinkedBibIds = record.unlinkedBibIds,
      modifiedDate = record.modifiedDate
    )
}
