package uk.ac.wellcome.mets_adapter.generators

import java.time.Instant

import uk.ac.wellcome.fixtures.RandomGenerators
import uk.ac.wellcome.models.pipeline.MetsSourceData

trait MetsSourceDataGenerators extends RandomGenerators {
  val olderDate: Instant = Instant.parse("1999-09-09T09:09:09Z")
  val newerDate: Instant = Instant.parse("2001-01-01T01:01:01Z")

  def createMetsSourceDataWith(
    bucket: String = randomAlphanumeric(),
    path: String = randomAlphanumeric(),
    file: String = randomAlphanumeric(),
    createdDate: Instant = Instant.now(),
    version: Int = 1
  ): MetsSourceData =
    MetsSourceData(
      bucket = bucket,
      path = path,
      version = version,
      file = file,
      createdDate = createdDate,
      deleted = false,
      manifestations = Nil)

  def createMetsSourceData: MetsSourceData =
    createMetsSourceDataWith()
}
