package weco.catalogue.source_model.generators

import java.time.Instant
import weco.storage.generators.S3ObjectLocationGenerators
import weco.storage.providers.s3.S3ObjectLocationPrefix
import weco.catalogue.source_model.mets.MetsFileWithImages

trait MetsSourceDataGenerators extends S3ObjectLocationGenerators {
  val olderDate: Instant = Instant.parse("1999-09-09T09:09:09Z")
  val newerDate: Instant = Instant.parse("2001-01-01T01:01:01Z")

  def createMetsSourceDataWith(
    bucket: String = createBucketName,
    path: String = randomAlphanumeric(),
    file: String = randomAlphanumeric(),
    modifiedTime: Instant = Instant.now(),
    version: Int = 1,
    manifestations: List[String] = Nil
  ): MetsFileWithImages =
    MetsFileWithImages(
      root = S3ObjectLocationPrefix(
        bucket = bucket,
        keyPrefix = path
      ),
      filename = file,
      modifiedTime = modifiedTime,
      version = version,
      manifestations = manifestations
    )

  def createMetsSourceData: MetsFileWithImages =
    createMetsSourceDataWith()
}
