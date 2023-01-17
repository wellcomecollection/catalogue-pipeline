package weco.pipeline.reindex_worker.models.source

import weco.storage.s3.S3ObjectLocation
import weco.catalogue.source_model.{
  CalmSourcePayload,
  MetsSourcePayload,
  MiroInventorySourcePayload,
  MiroSourcePayload,
  SierraSourcePayload,
  SourcePayload,
  TeiSourcePayload
}
import weco.catalogue.source_model.mets.MetsSourceData
import weco.catalogue.source_model.miro.{MiroSourceOverrides, MiroUpdateEvent}
import weco.catalogue.source_model.tei.TeiMetadata

sealed trait ReindexPayload {
  val id: String
  val version: Int

  def toSourcePayload: SourcePayload
}

case class CalmReindexPayload(
  id: String,
  payload: S3ObjectLocation,
  version: Int,
  isDeleted: Boolean = false
) extends ReindexPayload {

  override def toSourcePayload: SourcePayload =
    CalmSourcePayload(id, payload, version, isDeleted)
}

case class MiroInventoryReindexPayload(
  id: String,
  location: S3ObjectLocation,
  version: Int
) extends ReindexPayload {

  override def toSourcePayload: SourcePayload =
    MiroInventorySourcePayload(id, location, version)
}

case class MiroReindexPayload(
  id: String,
  isClearedForCatalogueAPI: Boolean,
  location: S3ObjectLocation,
  events: List[MiroUpdateEvent] = Nil,
  overrides: Option[MiroSourceOverrides] = None,
  version: Int
) extends ReindexPayload {

  override def toSourcePayload: SourcePayload =
    MiroSourcePayload(
      id = id,
      isClearedForCatalogueAPI = isClearedForCatalogueAPI,
      location = location,
      events = events,
      overrides = overrides,
      version = version
    )
}

case class MetsReindexPayload(
  id: String,
  payload: MetsSourceData,
  version: Int
) extends ReindexPayload {

  override def toSourcePayload: SourcePayload =
    MetsSourcePayload(id, payload, version)
}

case class TeiReindexPayload(
  id: String,
  payload: TeiMetadata,
  version: Int
) extends ReindexPayload {

  override def toSourcePayload: SourcePayload =
    TeiSourcePayload(id, payload, version)
}

case class SierraReindexPayload(
  id: String,
  payload: S3ObjectLocation,
  version: Int
) extends ReindexPayload {

  override def toSourcePayload: SourcePayload =
    SierraSourcePayload(id, payload, version)
}
