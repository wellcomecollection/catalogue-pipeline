package uk.ac.wellcome.platform.api.models

sealed trait MustQuery
sealed trait ImageMustQuery extends MustQuery

case class ColorMustQuery(hexColors: Seq[String]) extends ImageMustQuery
