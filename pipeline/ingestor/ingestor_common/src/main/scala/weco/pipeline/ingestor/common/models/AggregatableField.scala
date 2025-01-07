package weco.pipeline.ingestor.common.models

import weco.catalogue.internal_model.identifiers.IdState

case class AggregatableField(
  id: String,
  label: String
)

// Each AggregatableField has an ID and a label. The aggregation is based on the ID, and the label is displayed
// to the user in the front-end. For label-based aggregatable fields which do not have an ID (e.g. production dates),
// use the `fromLabel` method, which automatically sets the ID to the value of the label. The end result of this is
// the same as aggregating based on labels, with the added benefit of providing a consistent interface -- the frontend
// does not need to know whether a given aggregation is ID-based or label-based.
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
