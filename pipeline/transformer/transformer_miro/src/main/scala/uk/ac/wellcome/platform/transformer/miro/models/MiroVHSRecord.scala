package uk.ac.wellcome.platform.transformer.miro.models

import uk.ac.wellcome.storage.s3.S3ObjectLocation

case class MiroVHSRecord(
  id: String,
  version: Int,
  isClearedForCatalogueAPI: Boolean,
  location: S3ObjectLocation
) {
  def toMiroMetadata: MiroMetadata =
    MiroMetadata(isClearedForCatalogueAPI = isClearedForCatalogueAPI)
}
