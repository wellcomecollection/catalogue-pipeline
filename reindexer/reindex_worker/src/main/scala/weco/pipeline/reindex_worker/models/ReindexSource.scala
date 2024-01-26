package weco.pipeline.reindex_worker.models

import io.circe.Decoder

import scala.util.{Failure, Success}

sealed trait ReindexSource

object ReindexSource {
  case object Calm extends ReindexSource
  case object Tei extends ReindexSource
  case object Mets extends ReindexSource
  case object Miro extends ReindexSource
  case object MiroInventory extends ReindexSource
  case object Sierra extends ReindexSource

  implicit val decoder: Decoder[ReindexSource] = Decoder.decodeString.emapTry {
    case "calm"           => Success(ReindexSource.Calm)
    case "mets"           => Success(ReindexSource.Mets)
    case "miro"           => Success(ReindexSource.Miro)
    case "tei"            => Success(ReindexSource.Tei)
    case "miro_inventory" => Success(ReindexSource.MiroInventory)
    case "sierra"         => Success(ReindexSource.Sierra)
    case other            => Failure(new Throwable(s"Unrecognised ReindexSource: $other"))
  }
}
