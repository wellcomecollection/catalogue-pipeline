package weco.catalogue.source_model.generators

import java.time.Instant
import uk.ac.wellcome.storage.generators.S3ObjectLocationGenerators
import weco.catalogue.source_model.mets.MetsSourceData

trait MetsSourceDataGenerators extends S3ObjectLocationGenerators {
  val olderDate: Instant = Instant.parse("1999-09-09T09:09:09Z")
  val newerDate: Instant = Instant.parse("2001-01-01T01:01:01Z")

  def createMetsSourceDataWith(
    bucket: String = createBucketName,
    path: String = randomAlphanumeric(),
    file: String = randomAlphanumeric(),
    createdDate: Instant = Instant.now(),
    version: Int = 1,
    manifestations: List[String] = Nil
  ): MetsSourceData =
    MetsSourceData(
      bucket = bucket,
      path = path,
      version = version,
      file = file,
      createdDate = createdDate,
      deleted = false,
      manifestations = manifestations)

  def createMetsSourceData: MetsSourceData =
    createMetsSourceDataWith()
}
