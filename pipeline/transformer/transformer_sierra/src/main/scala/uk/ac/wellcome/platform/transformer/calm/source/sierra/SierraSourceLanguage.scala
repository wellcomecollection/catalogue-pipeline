package uk.ac.wellcome.platform.transformer.calm.source.sierra

// Represents a Language object, as returned by the Sierra API.
// https://techdocs.iii.com/sierraapi/Content/zReference/objects/bibObjectDescription.htm?Highlight=language
case class SierraSourceLanguage(code: String, name: String)
