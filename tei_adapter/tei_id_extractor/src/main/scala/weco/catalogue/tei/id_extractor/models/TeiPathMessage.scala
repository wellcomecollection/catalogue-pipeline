package weco.catalogue.tei.id_extractor.models

import io.circe.Decoder
import uk.ac.wellcome.json.JsonUtil._

import java.net.URI
import java.time.Instant
// Represents a path change coming from the tei_updater lambda
sealed trait TeiPathMessage{
  val path: String
}
case class TeiPathChangedMessage(path: String, uri: URI, timeModified: Instant) extends TeiPathMessage
case class TeiPathDeletedMessage(path: String, timeDeleted: Instant) extends TeiPathMessage
object TeiPathMessage {
  implicit val decoder: Decoder[TeiPathMessage] = Decoder[TeiPathChangedMessage].map[TeiPathMessage](identity).or(Decoder[TeiPathDeletedMessage].map[TeiPathMessage](identity))
}

