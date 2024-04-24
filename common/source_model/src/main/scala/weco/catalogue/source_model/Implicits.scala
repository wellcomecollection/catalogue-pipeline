package weco.catalogue.source_model

import io.circe.{Decoder, Encoder}
import io.circe.generic.extras.semiauto.{
  deriveConfiguredDecoder,
  deriveConfiguredEncoder
}
import weco.catalogue.source_model.calm.CalmRecord
import weco.catalogue.source_model.sierra._
import weco.catalogue.source_model.tei._
import weco.json.JsonUtil._
import weco.sierra.models.data._

object Implicits {
  // Cache these here to improve compilation times (otherwise they are
  // re-derived every time they are required).

  implicit val _decEbscoSourcePayload: Decoder[EbscoSourcePayload] =
    deriveConfiguredDecoder
  implicit val _encEbscoSourcePayload: Encoder[EbscoSourcePayload] =
    deriveConfiguredEncoder

  implicit val _decCalmSourcePayload: Decoder[CalmSourcePayload] =
    deriveConfiguredDecoder
  implicit val _encCalmSourcePayload: Encoder[CalmSourcePayload] =
    deriveConfiguredEncoder

  implicit val _decMiroInventorySourcePayload
    : Decoder[MiroInventorySourcePayload] =
    deriveConfiguredDecoder
  implicit val _encMiroInventorySourcePayload
    : Encoder[MiroInventorySourcePayload] =
    deriveConfiguredEncoder

  implicit val _decMiroSourcePayload: Decoder[MiroSourcePayload] =
    deriveConfiguredDecoder
  implicit val _encMiroSourcePayload: Encoder[MiroSourcePayload] =
    deriveConfiguredEncoder

  implicit val _decMetsSourcePayload: Decoder[MetsSourcePayload] =
    deriveConfiguredDecoder
  implicit val _encMetsSourcePayload: Encoder[MetsSourcePayload] =
    deriveConfiguredEncoder

  implicit val _decSierraSourcePayload: Decoder[SierraSourcePayload] =
    deriveConfiguredDecoder
  implicit val _encSierraSourcePayload: Encoder[SierraSourcePayload] =
    deriveConfiguredEncoder

  implicit val _decTeiSourcePayload: Decoder[TeiSourcePayload] =
    deriveConfiguredDecoder
  implicit val _encTeiSourcePayload: Encoder[TeiSourcePayload] =
    deriveConfiguredEncoder

  implicit val _decSourcePayload: Decoder[SourcePayload] =
    deriveConfiguredDecoder
  implicit val _encSourcePayload: Encoder[SourcePayload] =
    deriveConfiguredEncoder

  implicit val _decCalmRecord: Decoder[CalmRecord] =
    deriveConfiguredDecoder
  implicit val _encCalmRecord: Encoder[CalmRecord] =
    deriveConfiguredEncoder

  implicit val _decTeiIdChangeMessage: Decoder[TeiIdChangeMessage] =
    deriveConfiguredDecoder
  implicit val _encTeiIdChangeMessage: Encoder[TeiIdChangeMessage] =
    deriveConfiguredEncoder

  implicit val _decTeiIdDeletedMessage: Decoder[TeiIdDeletedMessage] =
    deriveConfiguredDecoder
  implicit val _encTeiIdDeletedMessage: Encoder[TeiIdDeletedMessage] =
    deriveConfiguredEncoder

  implicit val _decTeiIdMessage: Decoder[TeiIdMessage] =
    deriveConfiguredDecoder
  implicit val _encTeiIdMessage: Encoder[TeiIdMessage] =
    deriveConfiguredEncoder

  // TODO: Move the Sierra*Data implicits into scala-libs.
  implicit val _decSierraBibData: Decoder[SierraBibData] =
    deriveConfiguredDecoder
  implicit val _encSierraBibData: Encoder[SierraBibData] =
    deriveConfiguredEncoder

  implicit val _decSierraItemData: Decoder[SierraItemData] =
    deriveConfiguredDecoder
  implicit val _encSierraItemData: Encoder[SierraItemData] =
    deriveConfiguredEncoder

  implicit val _decSierraHoldingsData: Decoder[SierraHoldingsData] =
    deriveConfiguredDecoder
  implicit val _encSierraHoldingsData: Encoder[SierraHoldingsData] =
    deriveConfiguredEncoder

  implicit val _decSierraOrderData: Decoder[SierraOrderData] =
    deriveConfiguredDecoder
  implicit val _encSierraOrderData: Encoder[SierraOrderData] =
    deriveConfiguredEncoder

  implicit val _decSierraBibRecord: Decoder[SierraBibRecord] =
    deriveConfiguredDecoder
  implicit val _encSierraBibRecord: Encoder[SierraBibRecord] =
    deriveConfiguredEncoder

  implicit val _decSierraItemRecord: Decoder[SierraItemRecord] =
    deriveConfiguredDecoder
  implicit val _encSierraItemRecord: Encoder[SierraItemRecord] =
    deriveConfiguredEncoder

  implicit val _decSierraHoldingsRecord: Decoder[SierraHoldingsRecord] =
    deriveConfiguredDecoder
  implicit val _encSierraHoldingsRecord: Encoder[SierraHoldingsRecord] =
    deriveConfiguredEncoder

  implicit val _decSierraOrderRecord: Decoder[SierraOrderRecord] =
    deriveConfiguredDecoder
  implicit val _encSierraOrderRecord: Encoder[SierraOrderRecord] =
    deriveConfiguredEncoder

  implicit val _decSierraTransformable: Decoder[SierraTransformable] =
    deriveConfiguredDecoder
  implicit val _encSierraTransformable: Encoder[SierraTransformable] =
    deriveConfiguredEncoder
}
