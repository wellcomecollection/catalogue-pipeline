package uk.ac.wellcome.models.pipeline

import uk.ac.wellcome.storage.s3.S3ObjectLocation

sealed trait SourcePayload {
  val id: String
  val version: Int
}

case class CalmSourcePayload(
  id: String,
  version: Int,
  location: S3ObjectLocation
) extends SourcePayload

case class MetsSourcePayload(
  id: String,
  version: Int,
  payload: MetsSourceData
) extends SourcePayload {
  val sourceData: MetsSourceData = payload
}

case class MiroSourcePayload(
  id: String,
  version: Int,
  location: S3ObjectLocation,
  isClearedForCatalogueAPI: Boolean
) extends SourcePayload

case class SierraSourcePayload(
  id: String,
  version: Int,
  payload: S3ObjectLocation
) extends SourcePayload {
  val location: S3ObjectLocation = payload
}
