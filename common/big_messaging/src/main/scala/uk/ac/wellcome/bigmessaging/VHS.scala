package uk.ac.wellcome.bigmessaging

import java.util.UUID

import uk.ac.wellcome.storage.store.{
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

class VHS[T](val hybridStore: VHSInternalStore[T])
  extends VersionedHybridStore[
    String,
    Int,
    ObjectLocation,
    T,
  ](hybridStore)

class VHSInternalStore[T](
  prefix: ObjectLocationPrefix,
  indexStore: Store[Version[String, Int], ObjectLocation] with Maxima[String, Int],
  dataStore: TypedStore[ObjectLocation, T]
) extends HybridStoreWithMaxima[String, Int, ObjectLocation, T] {

  override val indexedStore = indexStore;
  override val typedStore = dataStore;

  override protected def createTypeStoreId(
    id: Version[String, Int]): ObjectLocation =
    prefix.asLocation(id.id, id.version.toString, UUID.randomUUID().toString)
}
