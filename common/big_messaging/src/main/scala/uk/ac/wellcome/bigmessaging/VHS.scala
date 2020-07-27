package uk.ac.wellcome.bigmessaging

import java.util.UUID

import uk.ac.wellcome.storage.store.{
  HybridStoreWithMaxima,
  Store,
  TypedStore,
  VersionedHybridStore
}
import uk.ac.wellcome.storage._
import uk.ac.wellcome.storage.maxima.Maxima

class VHS[TypedStoreLocation <: Location, T](
  val hybridStore: VHSInternalStore[TypedStoreLocation, T])
    extends VersionedHybridStore[
      String,
      Int,
      TypedStoreLocation,
      T,
    ](hybridStore)

class VHSInternalStore[TypedStoreLocation <: Location, T](
  prefix: Prefix[TypedStoreLocation],
  indexStore: Store[Version[String, Int], TypedStoreLocation] with Maxima[
    String,
    Int],
  dataStore: TypedStore[TypedStoreLocation, T]
) extends HybridStoreWithMaxima[String, Int, TypedStoreLocation, T] {

  override val indexedStore
    : Store[Version[String, Int], TypedStoreLocation] with Maxima[String, Int] =
    indexStore
  override val typedStore: TypedStore[TypedStoreLocation, T] = dataStore

  override protected def createTypeStoreId(
    id: Version[String, Int]): TypedStoreLocation =
    prefix.asLocation(id.id, id.version.toString, UUID.randomUUID().toString)
}
