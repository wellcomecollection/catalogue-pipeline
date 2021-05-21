package weco.catalogue.source_model.sierra.source

// Represents a Language object, as returned by the Sierra API.
// https://techdocs.iii.com/sierraapi/Content/zReference/objects/bibObjectDescription.htm?Highlight=language
case class SierraSourceLanguage(
  code: String,
  name: Option[String]
)

case object SierraSourceLanguage {
  def apply(code: String, name: String): SierraSourceLanguage =
    SierraSourceLanguage(code = code, name = Some(name))
}
