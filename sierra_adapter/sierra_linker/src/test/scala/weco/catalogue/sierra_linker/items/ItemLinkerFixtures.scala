package weco.catalogue.sierra_linker.items

import uk.ac.wellcome.sierra_adapter.model.{
  SierraBibNumber,
  SierraGenerators,
  SierraItemNumber,
  SierraItemRecord
}
import weco.catalogue.sierra_linker.{LinkerFixtures, LinkingRecord}

import java.time.Instant

trait ItemLinkerFixtures
    extends LinkerFixtures[SierraItemNumber, SierraItemRecord]
    with SierraGenerators {
  override def createId: SierraItemNumber = createSierraItemNumber

  override def createRecordWith(
    id: SierraItemNumber,
    modifiedDate: Instant,
    bibIds: List[SierraBibNumber],
    unlinkedBibIds: List[SierraBibNumber]): SierraItemRecord =
    createSierraItemRecordWith(
      id = id,
      modifiedDate = modifiedDate,
      bibIds = bibIds,
      unlinkedBibIds = unlinkedBibIds
    )

  override def createLinkingRecord(
    itemRecord: SierraItemRecord): LinkingRecord =
    LinkingRecord(itemRecord)

  override def getBibIds(itemRecord: SierraItemRecord): List[SierraBibNumber] =
    itemRecord.bibIds

  override def updateRecord(
    itemRecord: SierraItemRecord,
    unlinkedBibIds: List[SierraBibNumber]): SierraItemRecord =
    itemRecord.copy(unlinkedBibIds = unlinkedBibIds)

}
