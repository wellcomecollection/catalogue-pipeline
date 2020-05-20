package uk.ac.wellcome.bigmessaging

import java.util.UUID

import uk.ac.wellcome.storage.store.{
  HybridIndexedStoreEntry,
  HybridStoreEntry,
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
      id.id,
      id.version.toString,
      UUID.randomUUID().toString)
}

/** There are times when we want to be able to abstract away the fact that we
  * are using a VHS[T, EmptyMetadata], and instead use the more general Store
  * interface. However the standard VHS is a leaky abstraction, as it exposes
  * the fact that internally it uses a hybrid storage method, returning a
  * HybridStoreEntry[T, EmptyMetadata] rather than plainly T.
  *
  * Here we wrap the VHS to provide an interface that returns some item T
  * rather than a HybridStoreEntry[T, EmptyMetadata]
  */
class VHSWrapper[T](vhs: VHS[T, EmptyMetadata])
    extends Store[Version[String, Int], T]
    with Maxima[String, Int] {

  def get(id: Version[String, Int]): ReadEither =
    vhs.get(id).map {
      case Identified(key, HybridStoreEntry(item, _)) =>
        Identified(key, item)
    }

  def put(id: Version[String, Int])(item: T): WriteEither =
    vhs.put(id)(HybridStoreEntry(item, EmptyMetadata())).map {
      case Identified(key, HybridStoreEntry(item, _)) =>
        Identified(key, item)
    }

  def max(q: String): Either[ReadError, Int] =
    vhs.hybridStore.max(q)
}
