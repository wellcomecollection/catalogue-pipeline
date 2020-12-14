package uk.ac.wellcome.platform.reindex.reindex_worker.models.source

import uk.ac.wellcome.storage.s3.S3ObjectLocation
import weco.catalogue.source_model.{
  CalmSourcePayload,
  MetsSourcePayload,
  MiroInventorySourcePayload,
  MiroSourcePayload,
  SierraSourcePayload,
  SourcePayload
}
import weco.catalogue.source_model.mets.MetsSourceData

sealed trait ReindexPayload {
  val id: String
  val version: Int

  def toSourcePayload: SourcePayload
}

case class CalmReindexPayload(
  id: String,
  payload: S3ObjectLocation,
  version: Int
) extends ReindexPayload {

  override def toSourcePayload: SourcePayload =
    CalmSourcePayload(id, payload, version)
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
  version: Int
) extends ReindexPayload {

  override def toSourcePayload: SourcePayload =
    MiroSourcePayload(id, isClearedForCatalogueAPI, location, version)
}

case class MetsReindexPayload(
  id: String,
  payload: MetsSourceData,
  version: Int
) extends ReindexPayload {

  override def toSourcePayload: SourcePayload =
    MetsSourcePayload(id, payload, version)
}

case class SierraReindexPayload(
  id: String,
  payload: S3ObjectLocation,
  version: Int
) extends ReindexPayload {

  override def toSourcePayload: SourcePayload =
    SierraSourcePayload(id, payload, version)
}
