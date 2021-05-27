package weco.catalogue.source_model.sierra.marc

// Examples of fixedFields from the Sierra JSON:
//
//    "98": {
//      "label": "PDATE",
//      "value": "2017-12-22T12:55:57Z"
//    },
//    "77": {
//      "label": "TOT RENEW",
//      "value": 12
//    }
//
case class FixedField(
  label: String,
  value: String,
  display: Option[String] = None
)

case object FixedField {
  def apply(label: String, value: String, display: String): FixedField =
    FixedField(label = label, value = value, display = Some(display))
}
