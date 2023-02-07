package weco.catalogue.source_model.json

import io.circe.{Decoder, Encoder, HCursor, Json}

object JsonOps {
  // Sierra API responses can return some IDs as a String or an Int.
  // Parsing the value as an instance of this class will handle it for you.
  class StringOrInt(val underlying: String)

  implicit val decoder: Decoder[StringOrInt] =
    (c: HCursor) =>
      c.as[String] match {
        case Right(value) => Right(new StringOrInt(value))
        case Left(_) =>
          c.as[Int].map {
            v =>
              new StringOrInt(v.toString)
          }
      }

  implicit val encoder: Encoder[StringOrInt] =
    (id: StringOrInt) => Json.fromString(id.underlying)
}
