package uk.ac.wellcome.platform.snapshot_generator.akkastreams.source

import akka.NotUsed
import akka.stream.alpakka.s3.scaladsl.S3
import akka.stream.alpakka.s3.{ObjectMetadata, S3Attributes, S3Settings}
import akka.stream.scaladsl.Source
import grizzled.slf4j.Logging
import uk.ac.wellcome.storage.s3.S3ObjectLocation

object S3ObjectMetadataSource extends Logging {
  def apply(s3ObjectLocation: S3ObjectLocation,
            s3Settings: S3Settings): Source[ObjectMetadata, NotUsed] =
    S3.getObjectMetadata(
        bucket = s3ObjectLocation.bucket,
        key = s3ObjectLocation.key
      )
      .withAttributes(S3Attributes.settings(s3Settings))
      .map {
        case Some(objectMetadata) => objectMetadata
        case None =>
          val runtimeException = new RuntimeException(
            s"No object found at $s3ObjectLocation"
          )

          error(
            msg = "Failed getting ObjectMetadata!",
            t = runtimeException
          )

          throw runtimeException
      }
}
