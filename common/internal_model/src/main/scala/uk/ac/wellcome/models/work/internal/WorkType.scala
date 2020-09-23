package uk.ac.wellcome.models.work.internal

import io.circe.{Decoder, Encoder}
import io.circe.syntax._
import io.circe.generic.extras.semiauto.{
  deriveEnumerationDecoder,
  deriveEnumerationEncoder
}

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

  def getName(workType: WorkType): String = workType.asJson.asString.get
}
