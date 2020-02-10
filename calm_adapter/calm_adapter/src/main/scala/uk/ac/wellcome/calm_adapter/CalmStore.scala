package uk.ac.wellcome.calm_adapter

import uk.ac.wellcome.storage.{Identified, NoVersionExistsError, Version}
import uk.ac.wellcome.storage.store.VersionedStore

class CalmStore(store: VersionedStore[String, Int, CalmRecord]) {

  type Key = Version[String, Int]

  type Result[T] = Either[Throwable, T]

  def putRecord(record: CalmRecord): Result[Option[Key]] =
    shouldStoreRecord(record)
      .flatMap {
        case false => Right(None)
        case true =>
          store
            .putLatest(record.id)(record)
            .map { case Identified(key, _) => Some(key) }
            .left
            .map(_.e)
      }

  def shouldStoreRecord(record: CalmRecord): Result[Boolean] =
    store
      .getLatest(record.id)
      .map {
        case Identified(_, storedRecord) =>
          val sameTimestamp = record.retrievedAt == storedRecord.retrievedAt
          val differingData = record.data != storedRecord.data
          if (sameTimestamp && differingData)
            Left(
              new Exception("Cannot resolve latest data as timestamps are equal")
            )
          else
            Right(record.retrievedAt.isAfter(storedRecord.retrievedAt))
      }
      .left
      .flatMap {
        case NoVersionExistsError(_) => Right(Right(true))
        case err                     => Left(err.e)
      }
      .flatMap(identity)
}
