package uk.ac.wellcome.bigmessaging

import java.util.UUID
import scala.util.{Failure, Success, Try}

import uk.ac.wellcome.storage.store.{
  HybridIndexedStoreEntry,
  HybridStoreWithMaxima,
  Store,
  TypedStore,
  VersionedHybridStore
}
import uk.ac.wellcome.storage.{
  Identified,
  ObjectLocation,
  ObjectLocationPrefix,
  ReadError,
  Version
}
import uk.ac.wellcome.storage.maxima.Maxima

case class EmptyMetadata()

trait GetLocation {
  def getLocation(key: Version[String, Int]): Try[ObjectLocation]
}

class VHS[T, Metadata](val hybridStore: VHSInternalStore[T, Metadata])
    extends VersionedHybridStore[
      String,
      Int,
      ObjectLocation,
      T,
      Metadata
    ](hybridStore)
    with GetLocation {

  def getLocation(key: Version[String, Int]): Try[ObjectLocation] =
    hybridStore.indexedStore.get(key) match {
      case Right(Identified(_, entry)) => Success(entry.typedStoreId)
      case Left(error)                 => Failure(error.e)
    }
}

class VHSInternalStore[T, Metadata](
  prefix: ObjectLocationPrefix,
  indexStore: Store[
    Version[String, Int],
    HybridIndexedStoreEntry[ObjectLocation, Metadata]
  ] with Maxima[String, Int],
  dataStore: TypedStore[ObjectLocation, T]
) extends HybridStoreWithMaxima[String, Int, ObjectLocation, T, Metadata] {

  override val indexedStore = indexStore;
  override val typedStore = dataStore;

  override protected def createTypeStoreId(
    id: Version[String, Int]): ObjectLocation =
    prefix.asLocation(
      id.id.toString,
      id.version.toString,
      UUID.randomUUID().toString)
}

case class BackwardsCompatObjectLocation(namespace: String, key: String)

// The adaptor stores ObjectLocation with an older format to what is used by the
// recent storage libs: namely the `path` is stored as `key`. This store uses
// the old format internally whilst exposing the modern API
class BackwardsCompatIndexStore[T, Metadata](
  indexStore: Store[
    Version[String, Int],
    HybridIndexedStoreEntry[BackwardsCompatObjectLocation, Metadata]
  ] with Maxima[String, Int])
    extends Store[
      Version[String, Int],
      HybridIndexedStoreEntry[ObjectLocation, Metadata]]
    with Maxima[String, Int] {

  type IndexEntry[Location] = HybridIndexedStoreEntry[Location, Metadata]

  def get(id: Version[String, Int]): ReadEither =
    indexStore.get(id) match {
      case Left(error) => Left(error)
      case Right(Identified(id, entry)) =>
        Right(Identified(id, fromBackwardsCompat(entry)))
    }

  def put(id: Version[String, Int])(
    entry: HybridIndexedStoreEntry[ObjectLocation, Metadata]): WriteEither =
    indexStore.put(id)(toBackwardsCompat(entry)) match {
      case Left(error) => Left(error)
      case Right(Identified(id, entry)) =>
        Right(Identified(id, fromBackwardsCompat(entry)))
    }

  def max(q: String): Either[ReadError, Int] =
    indexStore.max(q)

  private def fromBackwardsCompat(entry: IndexEntry[BackwardsCompatObjectLocation]): IndexEntry[ObjectLocation] =
    entry match {
      case HybridIndexedStoreEntry(BackwardsCompatObjectLocation(namespace, path), metadata) =>
        HybridIndexedStoreEntry(ObjectLocation(namespace, path), metadata)
    }

  private def toBackwardsCompat(entry: IndexEntry[ObjectLocation]): IndexEntry[BackwardsCompatObjectLocation] =
    entry match {
      case HybridIndexedStoreEntry(ObjectLocation(namespace, path), metadata) =>
        HybridIndexedStoreEntry(BackwardsCompatObjectLocation(namespace, path), metadata)
    }
}
