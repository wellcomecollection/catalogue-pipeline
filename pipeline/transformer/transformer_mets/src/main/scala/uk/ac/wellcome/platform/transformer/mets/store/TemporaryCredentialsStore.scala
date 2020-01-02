package uk.ac.wellcome.platform.transformer.mets.store

import com.amazonaws.services.s3.AmazonS3
import uk.ac.wellcome.platform.transformer.mets.client.AssumeRoleClientProvider
import uk.ac.wellcome.storage.{Identified, ObjectLocation, StoreReadError}
import uk.ac.wellcome.storage.store.{Readable, TypedStoreEntry}
import uk.ac.wellcome.storage.store.s3.S3TypedStore
import uk.ac.wellcome.storage.streaming.Codec

class TemporaryCredentialsStore[T](
  assumeRoleClientProvider: AssumeRoleClientProvider[AmazonS3])(
  implicit codec: Codec[T])
    extends Readable[ObjectLocation, T] {

  def get(objectLocation: ObjectLocation): ReadEither = {
    for {
      client <- assumeRoleClientProvider.getClient.left
        .map(StoreReadError(_))
      result <- S3TypedStore[T](codec, client)
        .get(objectLocation)
    } yield {
      result match {
        case Identified(key, TypedStoreEntry(data, _)) => Identified(key, data)
      }
    }
  }
}
