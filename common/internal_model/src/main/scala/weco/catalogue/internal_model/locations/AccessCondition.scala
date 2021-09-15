package weco.catalogue.internal_model.locations

import io.circe.{Decoder, Encoder}
import weco.json.JsonUtil._

case class AccessCondition(
  method: AccessMethod,
  status: Option[AccessStatus] = None,
  note: Option[String] = None,
  terms: Option[String] = None
) {
  def isEmpty: Boolean =
    this == AccessCondition(method = AccessMethod.NotRequestable)

  def isAvailable: Boolean = status.exists(_.isAvailable)

  def hasRestrictions: Boolean = status.exists(_.hasRestrictions)
}

case object AccessCondition {
  def apply(method: AccessMethod, status: AccessStatus): AccessCondition =
    AccessCondition(method = method, status = Some(status))

  implicit val encoder: Encoder[AccessCondition] =
    Encoder.forProduct4[AccessCondition, AccessMethod, Option[AccessStatus], Option[String], Option[String]]("method", "status", "note", "terms")((ac: AccessCondition) => (ac.method, ac.status, ac.note, ac.terms))

  implicit val decoder: Decoder[AccessCondition] =
    Decoder.forProduct4[AccessCondition, AccessMethod, Option[AccessStatus], Option[String], Option[String]]("method", "status", "note", "terms")((method, status, note, terms) => AccessCondition(method, status, note, terms))
}
