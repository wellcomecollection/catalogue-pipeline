package uk.ac.wellcome.platform.transformer.mets.store

import com.amazonaws.services.s3.AmazonS3
import uk.ac.wellcome.platform.transformer.mets.client.AssumeRoleClientProvider
import uk.ac.wellcome.storage.ObjectLocation
import uk.ac.wellcome.storage.store.s3.S3TypedStore
import uk.ac.wellcome.storage.streaming.Codec

class TemporaryCredentialsStore[T](mestStoreAssumeRoleClient: AssumeRoleClientProvider[AmazonS3])(implicit codec: Codec[T]) {
  def get(objectLocation: ObjectLocation): Either[Throwable, T] = {
    for {
      client <- mestStoreAssumeRoleClient.getClient
      r <-S3TypedStore[T](codec, client)
        .get(objectLocation)
        .left.map(error => error.e)
    } yield (r.identifiedT.t)
  }
}
