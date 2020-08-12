package uk.ac.wellcome.bigmessaging.s3

import com.amazonaws.services.s3.AmazonS3
import software.amazon.awssdk.services.sns.SnsClient
import uk.ac.wellcome.bigmessaging.BigMessageSender
import uk.ac.wellcome.messaging.sns.{SNSConfig, SNSIndividualMessageSender}

object S3BigMessageSender {
  def apply(
    bucketName: String,
    snsConfig: SNSConfig,
    maxMessageSize: Int
  )(
    implicit s3Client: AmazonS3,
    snsClient: SnsClient
  ): BigMessageSender[SNSConfig] =
    new BigMessageSender[SNSConfig](
      underlying = new S3IndividualBigMessageSender(
        maxMessageSize = maxMessageSize,
        bucketName = bucketName,
        snsMessageSender = new SNSIndividualMessageSender(snsClient)
      ),
      subject = "Sent from S3BigMessageSender",
      destination = snsConfig
    )
}
