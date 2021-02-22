package weco.catalogue.sierra_linker.holdings

import uk.ac.wellcome.sierra_adapter.model.{SierraBibNumber, SierraGenerators, SierraHoldingsNumber, SierraHoldingsRecord}
import weco.catalogue.sierra_linker.{LinkerFixtures, LinkingRecord}

import java.time.Instant

trait HoldingsLinkerFixtures
    extends LinkerFixtures[SierraHoldingsNumber, SierraHoldingsRecord]
    with SierraGenerators {
  override def createId: SierraHoldingsNumber = createSierraHoldingsNumber

  override def createRecordWith(
    id: SierraHoldingsNumber,
    modifiedDate: Instant,
    bibIds: List[SierraBibNumber],
    unlinkedBibIds: List[SierraBibNumber]): SierraHoldingsRecord =
    createSierraHoldingsRecordWith(
      id = id,
      modifiedDate = modifiedDate,
      bibIds = bibIds,
      unlinkedBibIds = unlinkedBibIds
    )

  override def createLinkingRecord(
    holdingsRecord: SierraHoldingsRecord): LinkingRecord =
    LinkingRecord(holdingsRecord)

  override def getBibIds(holdingsRecord: SierraHoldingsRecord): List[SierraBibNumber] =
    holdingsRecord.bibIds

  override def updateRecord(
    holdingsRecord: SierraHoldingsRecord,
    unlinkedBibIds: List[SierraBibNumber]): SierraHoldingsRecord =
    holdingsRecord.copy(unlinkedBibIds = unlinkedBibIds)
}
