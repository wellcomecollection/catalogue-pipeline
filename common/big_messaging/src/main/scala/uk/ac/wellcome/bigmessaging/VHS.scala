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
  Version,
  ReadError
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

class BackwardsCompatIndexStore[T, Metadata](
  indexStore: Store[
    Version[String, Int],
    HybridIndexedStoreEntry[BackwardsCompatObjectLocation, Metadata]
  ] with Maxima[String, Int]) extends Store[Version[String, Int], HybridIndexedStoreEntry[ObjectLocation, Metadata]] with Maxima[String, Int] {

  def get(id: Version[String, Int]): ReadEither =
    indexStore.get(id) match {
      case Left(error) => Left(error)
      case Right(
        Identified(
          id,
          HybridIndexedStoreEntry(
            BackwardsCompatObjectLocation(namespace, path),
            metadata))) =>
        Right(
          Identified(
            id,
            HybridIndexedStoreEntry(
              ObjectLocation(namespace, path),
              metadata)))
    }

  def put(id: Version[String, Int])(item: HybridIndexedStoreEntry[ObjectLocation, Metadata]): WriteEither =
    throw new Exception("BackwardsCompatIndexStore is read only")

  def max(q: String): Either[ReadError, Int] =
    indexStore.max(q)
}
