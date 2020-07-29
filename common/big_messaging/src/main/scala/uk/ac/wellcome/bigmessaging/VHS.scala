package uk.ac.wellcome.bigmessaging

import java.util.UUID

import uk.ac.wellcome.storage.s3.{S3ObjectLocation, S3ObjectLocationPrefix}
import uk.ac.wellcome.storage.store.{
  HybridStoreWithMaxima,
  Store,
  TypedStore,
  VersionedHybridStore
}
import uk.ac.wellcome.storage.Version
import uk.ac.wellcome.storage.maxima.Maxima

class VHS[T](val hybridStore: VHSInternalStore[T])
    extends VersionedHybridStore[
      String,
      Int,
      S3ObjectLocation,
      T,
    ](hybridStore)

class VHSInternalStore[T](
  prefix: S3ObjectLocationPrefix,
  indexStore: Store[Version[String, Int], S3ObjectLocation] with Maxima[String,
                                                                      Int],
  dataStore: TypedStore[S3ObjectLocation, T]
) extends HybridStoreWithMaxima[String, Int, S3ObjectLocation, T] {

  override val indexedStore
    : Store[Version[String, Int], S3ObjectLocation] with Maxima[String, Int] =
    indexStore
  override val typedStore: TypedStore[S3ObjectLocation, T] = dataStore

  override protected def createTypeStoreId(
    id: Version[String, Int]): S3ObjectLocation =
    prefix.asLocation(id.id, id.version.toString, UUID.randomUUID().toString)
}
