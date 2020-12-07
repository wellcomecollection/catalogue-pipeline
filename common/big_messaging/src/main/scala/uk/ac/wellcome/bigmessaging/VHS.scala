package uk.ac.wellcome.bigmessaging

import java.util.UUID

import uk.ac.wellcome.storage.s3.{S3ObjectLocation, S3ObjectLocationPrefix}
import uk.ac.wellcome.storage.store.dynamo.DynamoHashStore
import uk.ac.wellcome.storage.store.s3.S3TypedStore
import uk.ac.wellcome.storage.store.{
  HybridStoreWithMaxima,
  VersionedHybridStore
}
import uk.ac.wellcome.storage.Version

class VHS[T](val hybridStore: VHSInternalStore[T])
    extends VersionedHybridStore[
      String,
      Int,
      S3ObjectLocation,
      T,
    ](hybridStore)

class VHSInternalStore[T](
  prefix: S3ObjectLocationPrefix)(
  implicit
  val indexedStore: DynamoHashStore[String, Int, S3ObjectLocation],
  val typedStore: S3TypedStore[T]
) extends HybridStoreWithMaxima[String, Int, S3ObjectLocation, T] {

  override protected def createTypeStoreId(
    id: Version[String, Int]): S3ObjectLocation =
    prefix.asLocation(id.id, id.version.toString, UUID.randomUUID().toString)
}
