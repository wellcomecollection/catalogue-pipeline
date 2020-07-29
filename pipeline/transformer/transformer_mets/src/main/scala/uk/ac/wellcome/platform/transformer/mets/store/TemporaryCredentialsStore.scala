package uk.ac.wellcome.platform.transformer.mets.store

import com.amazonaws.services.s3.AmazonS3
import uk.ac.wellcome.platform.transformer.mets.client.AssumeRoleClientProvider
import uk.ac.wellcome.storage.{Identified, StoreReadError}
import uk.ac.wellcome.storage.s3.S3ObjectLocation
import uk.ac.wellcome.storage.store.Readable
import uk.ac.wellcome.storage.store.s3.S3TypedStore
import uk.ac.wellcome.storage.streaming.Codec

class TemporaryCredentialsStore[T](
  assumeRoleClientProvider: AssumeRoleClientProvider[AmazonS3])(
  implicit codec: Codec[T])
    extends Readable[S3ObjectLocation, T] {

  def get(S3ObjectLocation: S3ObjectLocation): ReadEither = {
    for {
      client <- assumeRoleClientProvider.getClient.left
        .map(StoreReadError(_))
      result <- S3TypedStore[T](codec, client)
        .get(S3ObjectLocation)
    } yield {
      result match {
        case Identified(key, data) => Identified(key, data)
      }
    }
  }
}
