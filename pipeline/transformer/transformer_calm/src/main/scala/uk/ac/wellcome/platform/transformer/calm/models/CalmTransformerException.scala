package uk.ac.wellcome.platform.transformer.calm.models

sealed trait CalmTransformerException extends Throwable
object CalmTransformerException {
  case object TitleMissing extends CalmTransformerException
  case object RefNoMissing extends CalmTransformerException
  case object LevelMissing extends CalmTransformerException
  case object UnrecognisedLevel extends CalmTransformerException
}
