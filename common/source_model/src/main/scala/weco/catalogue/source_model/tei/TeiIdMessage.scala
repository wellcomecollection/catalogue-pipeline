package weco.catalogue.source_model.tei

import weco.storage.providers.s3.S3ObjectLocation

import java.time.Instant
// Represents a message for the tei_adapter with changes to id instead of file path
sealed trait TeiIdMessage {
  val id: String
}
case class TeiIdChangeMessage(
  id: String,
  s3Location: S3ObjectLocation,
  timeModified: Instant
) extends TeiIdMessage
case class TeiIdDeletedMessage(id: String, timeDeleted: Instant)
    extends TeiIdMessage

object TeiIdMessage {
  implicit class TeiIdMessageToTeiMetadata(message: TeiIdMessage) {
    def toMetadata = message match {
      case TeiIdChangeMessage(_, s3Location, timeModified) =>
        TeiChangedMetadata(
          s3Location,
          timeModified
        )
      case TeiIdDeletedMessage(_, timeDeleted) =>
        TeiDeletedMetadata(
          timeDeleted
        )
    }
  }
}
