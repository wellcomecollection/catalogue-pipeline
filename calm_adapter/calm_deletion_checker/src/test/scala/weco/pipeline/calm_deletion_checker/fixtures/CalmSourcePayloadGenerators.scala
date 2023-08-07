package weco.pipeline.calm_deletion_checker.fixtures

import java.util.UUID
import weco.storage.generators.S3ObjectLocationGenerators
import weco.storage.providers.s3.S3ObjectLocation
import weco.catalogue.source_model.CalmSourcePayload
import weco.pipeline.calm_deletion_checker.CalmSourceDynamoRow

case class CalmSourceDynamoRowWithoutDeletionFlag(
  id: String,
  version: Int,
  payload: S3ObjectLocation
) {
  def toPayload: CalmSourcePayload =
    CalmSourcePayload(
      id = id,
      version = version,
      location = payload
    )
}

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

  implicit class CalmSourcePayloadOps(payload: CalmSourcePayload) {
    def toDynamoRow: CalmSourceDynamoRow = CalmSourceDynamoRow(
      id = payload.id,
      version = payload.version,
      payload = payload.location,
      isDeleted = payload.isDeleted
    )

    def toDynamoRowWithoutDeletionFlag: CalmSourceDynamoRowWithoutDeletionFlag =
      CalmSourceDynamoRowWithoutDeletionFlag(
        id = payload.id,
        version = payload.version,
        payload = payload.location
      )
  }

}
