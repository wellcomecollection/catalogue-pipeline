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
  Version
}
import uk.ac.wellcome.storage.maxima.Maxima

case class EmptyMetadata()

trait GetLocation {
  def getLocation(key: Version[String, Int]): Try[ObjectLocation]
}

class VHS[T](override val hybridStore: VHSInternalStore[T, EmptyMetadata])
    extends VHSWithMetadata[T, EmptyMetadata](hybridStore)

class VHSWithMetadata[T, Metadata](
  val hybridStore: VHSInternalStore[T, Metadata])
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
