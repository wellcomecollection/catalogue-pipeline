package weco.pipeline.calm_deletion_checker

import weco.storage.providers.s3.S3ObjectLocation
import weco.catalogue.source_model.CalmSourcePayload

case class CalmSourceDynamoRow(
  id: String,
  version: Int,
  payload: S3ObjectLocation,
  isDeleted: Boolean = false
) {
  def toPayload: CalmSourcePayload =
    CalmSourcePayload(
      id = id,
      version = version,
      location = payload,
      isDeleted = isDeleted
    )
}
