package uk.ac.wellcome.bigmessaging.s3

import com.amazonaws.services.s3.AmazonS3
import uk.ac.wellcome.bigmessaging.IndividualBigMessageSender
import uk.ac.wellcome.messaging.IndividualMessageSender
import uk.ac.wellcome.messaging.sns.{SNSConfig, SNSIndividualMessageSender}
import uk.ac.wellcome.storage.s3.S3ObjectLocation
import uk.ac.wellcome.storage.store.Store
import uk.ac.wellcome.storage.store.s3.S3TypedStore

class S3IndividualBigMessageSender(
  val maxMessageSize: Int,
  bucketName: String,
  snsMessageSender: SNSIndividualMessageSender
)(
  implicit s3Client: AmazonS3
) extends IndividualBigMessageSender[SNSConfig] {
  override val underlying: IndividualMessageSender[SNSConfig] = snsMessageSender

  override val store: Store[S3ObjectLocation, String] = S3TypedStore[String]

  override val namespace: String = bucketName
}
