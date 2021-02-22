package weco.catalogue.sierra_linker

import uk.ac.wellcome.sierra_adapter.model.{
  AbstractSierraRecord,
  SierraBibNumber,
  SierraTypedRecordNumber
}

import java.time.Instant

trait LinkerFixtures[
  Id <: SierraTypedRecordNumber, Record <: AbstractSierraRecord[Id]] {
  def createId: Id

  def createRecord: Record =
    createRecordWith(
      modifiedDate = Instant.now(),
      bibIds = List.empty
    )

  def createRecordWith(
    id: Id = createId,
    modifiedDate: Instant,
    bibIds: List[SierraBibNumber],
    unlinkedBibIds: List[SierraBibNumber] = List.empty): Record

  def createLinkingRecord(record: Record): LinkingRecord

  def updateRecord(record: Record,
                   unlinkedBibIds: List[SierraBibNumber]): Record

  def getBibIds(record: Record): List[SierraBibNumber]
}
