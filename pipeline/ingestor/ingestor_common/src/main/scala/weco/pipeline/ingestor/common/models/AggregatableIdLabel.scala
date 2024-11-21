package weco.pipeline.ingestor.common.models

import weco.catalogue.internal_model.identifiers.IdState

case class AggregatableIdLabel(
  id: Option[String],
  label: String
)

case object AggregatableIdLabel {
  def fromId(id: Option[String], label: String): AggregatableIdLabel = {
    AggregatableIdLabel(id = id, label = label.stripSuffix("."))
  }

  def fromIdState(
    idState: Option[IdState],
    label: String
  ): AggregatableIdLabel =
    fromId(idState.flatMap(_.maybeCanonicalId).map(_.underlying), label)
}
