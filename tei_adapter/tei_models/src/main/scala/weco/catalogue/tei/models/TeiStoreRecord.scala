package weco.catalogue.tei.models

import uk.ac.wellcome.storage.s3.S3ObjectLocation

import java.time.ZonedDateTime

case class TeiStoreRecord(id: String, metadata: TeiMetadata, version: Int)

case class TeiMetadata(deleted: Boolean, s3Location: S3ObjectLocation, timeModified: ZonedDateTime)
