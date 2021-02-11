package uk.ac.wellcome.platform.calm_deletion_checker.fixtures

import java.util.UUID

import uk.ac.wellcome.storage.generators.S3ObjectLocationGenerators
import uk.ac.wellcome.storage.s3.S3ObjectLocation
import weco.catalogue.source_model.CalmSourcePayload

trait CalmSourcePayloadGenerators extends S3ObjectLocationGenerators {

  def calmSourcePayloadWith(
    id: String = UUID.randomUUID().toString,
    location: S3ObjectLocation = createS3ObjectLocation,
    version: Int = randomInt(from = 1, to = 10),
    isDeleted: Boolean = false
  ): CalmSourcePayload =
    CalmSourcePayload(
      id = id,
      location = location,
      version = version,
      isDeleted = isDeleted
    )

  def calmSourcePayload: CalmSourcePayload = calmSourcePayloadWith()

}
