package weco.catalogue.source_model.ebsco

import weco.storage.providers.s3.S3ObjectLocation

sealed trait EbscoSourceData {}
case class EbscoUpdatedSourceData(s3Location: S3ObjectLocation)
    extends EbscoSourceData
case object EbscoDeletedSourceData extends EbscoSourceData
