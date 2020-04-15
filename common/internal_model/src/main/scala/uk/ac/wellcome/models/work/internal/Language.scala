package uk.ac.wellcome.models.work.internal

import scala.io.Source

import uk.ac.wellcome.models.work.internal.result.Result

case class Language(
  id: String,
  label: String,
  ontologyType: String = "Language"
)

object Language {

  def fromCode(code: String): Result[Language] =
    languageCodeMap
      .get(code)
      .map(label => Right(Language(code, label)))
      .getOrElse {
        Left(new Exception(s"Invalid ISO 693-2 language code: $code"))
      }

  def fromLabel(label: String): Result[Language] =
    languageLabelMap
      .get(label)
      .map(code => Right(Language(code, label)))
      .getOrElse {
        Left(new Exception(s"Unrecognised language label: $label"))
      }

  private def languageCodes: List[(String, String)] =
    Source
      .fromInputStream(
        getClass.getResourceAsStream("/language-codes.csv")
      )
      .getLines
      .map(_.split(",", 2).toList)
      .map { case List(code, label) => (code, label.replace("\"", "")) }
      .toList

  lazy private val languageCodeMap =
    languageCodes.toMap

  lazy private val languageLabelMap =
    languageCodes
      .flatMap { case (code, label) =>
          label.split(";").toList.map(label => (label.trim, code))
      }.toMap
}
