package uk.ac.wellcome.platform.api.models

sealed trait WorkMustQuery

sealed trait ImageMustQuery

case class ColorMustQuery(hexColors: Seq[String]) extends ImageMustQuery
