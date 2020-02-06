package uk.ac.wellcome.calm_adapter

import uk.ac.wellcome.storage.{Identified, NoVersionExistsError, Version}
import uk.ac.wellcome.storage.store.VersionedStore

class CalmStore(store: VersionedStore[String, Int, Map[String, String]]) {

  type Key = Version[String, Int]

  type Result[T] = Either[Throwable, T]

  def putRecord(record: CalmRecord): Result[Option[Key]] =
    shouldStoreRecord(record)
      .flatMap {
        case false => Right(None)
        case true =>
          store
            .putLatest(record.id)(record.data)
            .map { case Identified(key, _) => Some(key) }
            .left
            .map(_.e)
      }

  def shouldStoreRecord(record: CalmRecord): Result[Boolean] =
    store
      .getLatest(record.id)
      .map { case Identified(_, storedData) => record.data != storedData }
      .left
      .flatMap {
        case NoVersionExistsError(_) => Right(true)
        case err                     => Left(err.e)
      }
}
