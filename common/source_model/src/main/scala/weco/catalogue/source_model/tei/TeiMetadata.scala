package weco.catalogue.source_model.tei

import weco.storage.providers.s3.S3ObjectLocation

import java.time.Instant

sealed trait TeiMetadata {
  val time: Instant
}
case class TeiChangedMetadata(s3Location: S3ObjectLocation, time: Instant)
    extends TeiMetadata
case class TeiDeletedMetadata(time: Instant) extends TeiMetadata
