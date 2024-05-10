package weco.catalogue.source_model.ebsco

import weco.storage.providers.s3.S3ObjectLocation

import java.time.Instant

sealed trait EbscoSourceData {
  val modifiedTime: Instant
}
case class EbscoUpdatedSourceData(
                                   s3Location: S3ObjectLocation,
                                   modifiedTime: Instant
                                 )
    extends EbscoSourceData
case class EbscoDeletedSourceData(modifiedTime: Instant) extends EbscoSourceData
