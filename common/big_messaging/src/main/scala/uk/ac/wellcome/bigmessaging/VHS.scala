package uk.ac.wellcome.bigmessaging

import java.util.UUID

import uk.ac.wellcome.storage.store.{
  HybridIndexedStoreEntry,
  HybridStoreWithMaxima,
  Store,
  TypedStore,
  VersionedHybridStore
}
import uk.ac.wellcome.storage.{
  ObjectLocation,
  ObjectLocationPrefix,
  Version
}
import uk.ac.wellcome.storage.maxima.Maxima

case class EmptyMetadata()

class VHS[T, Metadata](val hybridStore: VHSInternalStore[T, Metadata])
    extends VersionedHybridStore[
      String,
      Int,
      ObjectLocation,
      T,
      Metadata
    ](hybridStore)

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
