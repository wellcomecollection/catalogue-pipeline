package weco.catalogue.tei.id_extractor.models

import io.circe.Decoder
import uk.ac.wellcome.json.JsonUtil._

import java.net.URI
import java.time.ZonedDateTime

sealed trait TeiPathMessage{
  val path: String
}
case class TeiPathChangedMessage(path: String, uri: URI, timeModified: ZonedDateTime) extends TeiPathMessage
case class TeiPathDeletedMessage(path: String, timeDeleted: ZonedDateTime) extends TeiPathMessage
object TeiPathMessage {
  implicit val decoder: Decoder[TeiPathMessage] = Decoder[TeiPathChangedMessage].map[TeiPathMessage](identity).or(Decoder[TeiPathDeletedMessage].map[TeiPathMessage](identity))
}

