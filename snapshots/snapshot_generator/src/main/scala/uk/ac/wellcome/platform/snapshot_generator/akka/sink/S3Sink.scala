package uk.ac.wellcome.platform.snapshot_generator.akka.sink

import akka.stream.alpakka.s3.scaladsl.S3
import akka.stream.alpakka.s3.{MultipartUploadResult, S3Attributes, S3Settings}
import akka.stream.scaladsl.Sink
import akka.util.ByteString
import uk.ac.wellcome.storage.s3.S3ObjectLocation

import scala.concurrent.Future

object S3Sink {
  def apply(akkaS3Settings: S3Settings)(s3ObjectLocation: S3ObjectLocation)
    : Sink[ByteString, Future[MultipartUploadResult]] =
    S3.multipartUpload(
        bucket = s3ObjectLocation.bucket,
        key = s3ObjectLocation.key
      )
      .withAttributes(S3Attributes.settings(akkaS3Settings))
}
