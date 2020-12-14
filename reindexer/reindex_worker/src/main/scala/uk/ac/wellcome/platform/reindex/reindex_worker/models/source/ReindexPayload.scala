package uk.ac.wellcome.platform.reindex.reindex_worker.models.source

import uk.ac.wellcome.mets_adapter.models.MetsSourceData
import uk.ac.wellcome.storage.s3.S3ObjectLocation

sealed trait ReindexPayload {
  val id: String
  val version: Int
}

case class CalmReindexPayload(
  id: String,
  payload: S3ObjectLocation,
  version: Int
) extends ReindexPayload

case class MiroInventoryReindexPayload(
  id: String,
  location: S3ObjectLocation,
  version: Int
) extends ReindexPayload

case class MiroReindexPayload(
  id: String,
  isClearedForCatalogueAPI: Boolean,
  location: S3ObjectLocation,
  version: Int
) extends ReindexPayload

case class MetsReindexPayload(
  id: String,
  payload: MetsSourceData,
  version: Int
) extends ReindexPayload

case class SierraReindexPayload(
  id: String,
  payload: S3ObjectLocation,
  version: Int
) extends ReindexPayload
