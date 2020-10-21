package uk.ac.wellcome.calm_adapter

import grizzled.slf4j.Logging

import uk.ac.wellcome.storage.{
  Identified,
  NoVersionExistsError,
  StorageError,
  Version
}
import uk.ac.wellcome.storage.store.VersionedStore

class CalmStore(store: VersionedStore[String, Int, CalmRecord])
    extends Logging {

  type Key = Version[String, Int]

  type Result[T] = Either[Throwable, T]

  def putRecord(record: CalmRecord): Result[Option[(Key, CalmRecord)]] = {
    val recordSummmary = s"ID=${record.id}, RefNo=${record.refNo.getOrElse(
      "NONE")}, Modified=${record.modified.getOrElse("NONE")}"
    shouldStoreRecord(record)
      .flatMap {
        case false =>
          info(s"Ignoring calm record: $recordSummmary")
          Right(None)
        case true =>
          info(s"Storing calm record: $recordSummmary")
          store
            .putLatest(record.id)(record)
            .map { case Identified(key, record) => Some(key -> record) }
            .left
            .map(toReadableException)
      }
  }

  def setRecordPublished(key: Key, record: CalmRecord): Result[Key] =
    store
      .put(Version(key.id, key.version + 1))(record.copy(published = true))
      .map { case Identified(key, _) => key }
      .left
      .map(toReadableException)

  def shouldStoreRecord(record: CalmRecord): Result[Boolean] =
    store
      .getLatest(record.id)
      .map {
        case Identified(_, storedRecord) =>
          checkResolvable(record, storedRecord)
            .map(Left(_))
            .getOrElse(Right(compareRecords(record, storedRecord)))
      }
      .left
      .flatMap {
        case NoVersionExistsError(_) => Right(Right(true))
        case err                     => Left(err.e)
      }
      .flatMap(identity)

  def checkResolvable(record: CalmRecord,
                      storedRecord: CalmRecord): Option[Exception] = {
    val sameTimestamp = record.retrievedAt == storedRecord.retrievedAt
    val differingData = record.data != storedRecord.data
    if (sameTimestamp && differingData)
      Some(
        new Exception("Cannot resolve latest data as timestamps are equal")
      )
    else
      None
  }

  def compareRecords(record: CalmRecord, storedRecord: CalmRecord): Boolean =
    record.retrievedAt.isAfter(storedRecord.retrievedAt) &&
      (record.data != storedRecord.data || !storedRecord.published)

  /** Errors in the storage library sometimes wrap empty Exception objects with
    * no message, meaning if we return them directly we lose any indication as
    * to what has occured. Here we make sure the Exception is somewhat readable.
    */
  def toReadableException(err: StorageError): Throwable =
    if (Option(err.e.getMessage).exists(_.trim.nonEmpty))
      err.e
    else
      new Exception(err.getClass.getSimpleName)
}
