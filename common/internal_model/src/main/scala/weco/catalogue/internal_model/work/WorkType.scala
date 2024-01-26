package weco.catalogue.internal_model.work

import io.circe.generic.extras.semiauto.{deriveEnumerationDecoder, deriveEnumerationEncoder}
import io.circe.syntax._
import io.circe.{Decoder, Encoder}

sealed trait WorkType

object WorkType {
  case object Standard extends WorkType
  case object Collection extends WorkType
  case object Series extends WorkType
  case object Section extends WorkType

  implicit val workTypeEncoder: Encoder[WorkType] =
    deriveEnumerationEncoder[WorkType]
  implicit val workTypeDecoder: Decoder[WorkType] =
    deriveEnumerationDecoder[WorkType]

  // This is used in the API for the WorkType filter.
  def getName(workType: WorkType): String = workType.asJson.asString.get
}
