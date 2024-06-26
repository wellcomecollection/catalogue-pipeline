package weco.catalogue.source_model

import weco.storage.providers.s3.S3ObjectLocation
import weco.catalogue.source_model.mets.MetsSourceData
import weco.catalogue.source_model.miro.{MiroSourceOverrides, MiroUpdateEvent}
import weco.catalogue.source_model.tei.TeiMetadata

import java.time.Instant

sealed trait SourcePayload {
  val id: String
  val version: Int
}

case class EbscoSourcePayload(
  id: String,
  location: Option[S3ObjectLocation],
  version: Int,
  sha256: Option[String],
  deleted: Boolean = false,
  time: Instant
) extends SourcePayload

case class CalmSourcePayload(
  id: String,
  location: S3ObjectLocation,
  version: Int,
  isDeleted: Boolean = false
) extends SourcePayload

case class MiroInventorySourcePayload(
  id: String,
  location: S3ObjectLocation,
  version: Int
) extends SourcePayload

case class MiroSourcePayload(
  id: String,
  isClearedForCatalogueAPI: Boolean,
  location: S3ObjectLocation,
  events: List[MiroUpdateEvent],
  overrides: Option[MiroSourceOverrides],
  version: Int
) extends SourcePayload

case class MetsSourcePayload(
  id: String,
  sourceData: MetsSourceData,
  version: Int
) extends SourcePayload

case class SierraSourcePayload(
  id: String,
  location: S3ObjectLocation,
  version: Int
) extends SourcePayload

case class TeiSourcePayload(id: String, metadata: TeiMetadata, version: Int)
    extends SourcePayload
