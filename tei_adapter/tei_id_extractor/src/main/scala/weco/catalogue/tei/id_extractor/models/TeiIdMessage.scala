package weco.catalogue.tei.id_extractor.models

import uk.ac.wellcome.storage.s3.S3ObjectLocation

import java.time.Instant
// Represents a message for the tei_adapter with changes to id instead of file path
sealed trait TeiIdMessage
case class TeiIdChangeMessage(id: String,
                              s3Location: S3ObjectLocation,
                              timeModified: Instant)
    extends TeiIdMessage
case class TeiIdDeletedMessage(id: String, timeDeleted: Instant)
    extends TeiIdMessage
