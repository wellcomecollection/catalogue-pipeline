package weco.catalogue.tei.id_extractor.models

import uk.ac.wellcome.storage.s3.S3ObjectLocation

import java.time.ZonedDateTime

sealed trait TeiIdMessage
case class TeiIdChangeMessage(id: String, s3Location: S3ObjectLocation, timeModified: ZonedDateTime) extends TeiIdMessage
case class TeiIdDeletedMessage(id: String, timeDeleted: ZonedDateTime)extends TeiIdMessage
