package uk.ac.wellcome.bigmessaging

import uk.ac.wellcome.storage.s3.{S3ObjectLocation, S3ObjectLocationPrefix}
import uk.ac.wellcome.storage.store.dynamo.{DynamoHashStore, DynamoHybridStore}
import uk.ac.wellcome.storage.store.s3.S3TypedStore
import uk.ac.wellcome.storage.store.{
  HybridStoreWithMaxima,
  VersionedHybridStore
}

class VHS[T](val hybridStore: VHSInternalStore[T])
    extends VersionedHybridStore[
      String,
      Int,
      S3ObjectLocation,
      T,
    ](hybridStore)

class VHSInternalStore[T](prefix: S3ObjectLocationPrefix)(
  implicit
  indexedStore: DynamoHashStore[String, Int, S3ObjectLocation],
  typedStore: S3TypedStore[T]
) extends DynamoHybridStore[T](prefix)(indexedStore, typedStore)
    with HybridStoreWithMaxima[String, Int, S3ObjectLocation, T]
