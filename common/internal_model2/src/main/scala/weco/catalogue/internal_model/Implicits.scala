package weco.catalogue.internal_model

import io.circe.generic.extras.semiauto._
import io.circe._
import weco.catalogue.internal_model.locations.AccessCondition
import weco.catalogue.internal_model.work.Note
import weco.json.JsonUtil._

object Implicits {

  // Cache these here to improve compilation times (otherwise they are
  // re-derived every time they are required).
  //
  // The particular implicits defined here have been chosen by generating
  // flamegraphs using the scalac-profiling plugin. See this blog post for
  // info: https://www.scala-lang.org/blog/2018/06/04/scalac-profiling.html
  //
  // NOTE: the ordering here is important: we derive ImageData[_]
  // then WorkData, then the other images and works due to the order of
  // dependencies (thus preventing duplicate work)

  implicit val _decAccessCondition: Decoder[AccessCondition] =
    deriveConfiguredDecoder
  implicit val _decNote: Decoder[Note] = deriveConfiguredDecoder

  implicit val _encAccessCondition: Encoder[AccessCondition] =
    deriveConfiguredEncoder
  implicit val _encNote: Encoder[Note] = deriveConfiguredEncoder
}
