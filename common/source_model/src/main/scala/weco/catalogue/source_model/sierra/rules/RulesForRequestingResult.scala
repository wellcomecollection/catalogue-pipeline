package weco.catalogue.source_model.sierra.rules

sealed trait RulesForRequestingResult

case object Requestable extends RulesForRequestingResult

case class NotRequestable(message: Option[String] = None)
  extends RulesForRequestingResult

case object NotRequestable {
  def apply(message: String): NotRequestable =
    NotRequestable(message = Some(message))
}
