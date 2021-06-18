package weco.catalogue.source_model.tei

import uk.ac.wellcome.storage.s3.S3ObjectLocation

import java.time.Instant

case class TeiMetadata(deleted: Boolean, s3Location: S3ObjectLocation, timeModified: Instant)
