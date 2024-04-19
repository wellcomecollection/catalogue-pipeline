package weco.catalogue.source_model.ebsco

import weco.storage.providers.s3.S3ObjectLocation

sealed trait EbscoSourceData {
  val version: Int
}
case class EbscoChangedSourceData(s3Location: S3ObjectLocation, version: Int)
  extends EbscoSourceData
case class EbscoDeletedSourceData(version: Int) extends EbscoSourceData
