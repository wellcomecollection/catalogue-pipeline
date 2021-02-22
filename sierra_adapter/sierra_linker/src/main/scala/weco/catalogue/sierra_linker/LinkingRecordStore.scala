package weco.catalogue.sierra_linker

import uk.ac.wellcome.sierra_adapter.model.{
  AbstractSierraRecord,
  SierraBibNumber,
  SierraTypedRecordNumber
}
import uk.ac.wellcome.storage.store.VersionedStore
import uk.ac.wellcome.storage.{Identified, UpdateNotApplied}

trait LinkingRecordStore[
  Id <: SierraTypedRecordNumber, Record <: AbstractSierraRecord[Id]] {
  val store: VersionedStore[Id, Int, LinkingRecord]

  def update(newRecord: Record): Either[Throwable, Option[Record]] = {
    val newLink = createNewLink(newRecord)

    val upsertResult: store.UpdateEither =
      store.upsert(newRecord.id)(newLink) {
        updateLink(_, newRecord) match {
          case Some(updatedLink) => Right(updatedLink)
          case None =>
            Left(
              UpdateNotApplied(
                new Throwable(s"Item ${newRecord.id} is already up-to-date")))
        }
      }

    upsertResult match {
      case Right(Identified(_, updatedLink)) =>
        val updatedRecord = updateRecord(
          newRecord,
          unlinkedBibIds = updatedLink.unlinkedBibIds
        )
        Right(Some(updatedRecord))

      case Left(_: UpdateNotApplied) => Right(None)
      case Left(err)                 => Left(err.e)
    }
  }

  def createNewLink(record: Record): LinkingRecord

  def updateLink(existingLink: LinkingRecord,
                 record: Record): Option[LinkingRecord]

  def updateRecord(record: Record,
                   unlinkedBibIds: List[SierraBibNumber]): Record
}
