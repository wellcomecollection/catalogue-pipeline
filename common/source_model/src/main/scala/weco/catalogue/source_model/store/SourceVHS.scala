package weco.catalogue.source_model.store

import weco.storage.s3.S3ObjectLocation
import weco.storage.store.VersionedHybridStore
import weco.storage.{Identified, StorageError, Version}

// A wrapper around a VHS that exposes the underlying S3 location
// when you call certain methods.
class SourceVHS[T](
  val underlying: VersionedHybridStore[String, Int, S3ObjectLocation, T]
) {
  private val indexedStore = underlying.hybridStore.indexedStore

  private def wrappedOp(
    f: Either[StorageError, Identified[Version[String, Int], T]]
  ): Either[StorageError, Identified[
    Version[String, Int],
    (S3ObjectLocation, T)
  ]] =
    f.flatMap {
      case Identified(id, record) =>
        indexedStore.get(id).map {
          case Identified(_, location) =>
            Identified(id, (location, record))
        }
    }

  def putLatest(id: String)(t: T): Either[StorageError, Identified[
    Version[String, Int],
    (S3ObjectLocation, T)
  ]] =
    wrappedOp {
      underlying.putLatest(id)(t)
    }

  def update(
    id: String
  )(f: underlying.UpdateFunction): Either[StorageError, Identified[
    Version[String, Int],
    (S3ObjectLocation, T)
  ]] =
    wrappedOp {
      underlying.update(id)(f)
    }

  def upsert(
    id: String
  )(t: T)(f: underlying.UpdateFunction): Either[StorageError, Identified[
    Version[String, Int],
    (S3ObjectLocation, T)
  ]] =
    wrappedOp {
      underlying.upsert(id)(t)(f)
    }
}
