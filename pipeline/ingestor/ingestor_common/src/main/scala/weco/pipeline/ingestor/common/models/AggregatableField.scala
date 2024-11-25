package weco.pipeline.ingestor.common.models

import weco.catalogue.internal_model.identifiers.IdState

case class AggregatableField(
  id: String,
  label: String
)

case object AggregatableField {
  private def normaliseLabel(label: String) = {
    label.stripSuffix(".")
  }
  def apply(id: String, label: String): AggregatableField = {
    new AggregatableField(id = id, label = normaliseLabel(label))
  }

  def fromIdState(
    idState: Option[IdState],
    label: String
  ): AggregatableField = {
    val id = idState.flatMap(_.maybeCanonicalId.map(_.underlying))

    id match {
      case Some(id) => apply(id, label)
      case _        => fromLabel(label)
    }
  }

  def fromLabel(label: String): AggregatableField =
    new AggregatableField(
      id = normaliseLabel(label),
      label = normaliseLabel(label)
    )
}
