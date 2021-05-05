package uk.ac.wellcome.models.index

import com.sksamuel.elastic4s.ElasticDsl._
import WorksAnalysis._
import com.sksamuel.elastic4s.requests.mappings.KeywordField

/** Mixin for common fields used within an IndexConfig in our internal models.
  */
trait IndexConfigFields {
  def englishTextKeywordField(name: String) =
    textField(name).fields(
      keywordField("keyword"),
      textField("english").analyzer("english")
    )

  def englishTextField(name: String) =
    textField(name).fields(
      textField("english").analyzer("english")
    )

  val languagesTextFields =
    languages.map(lang => textField(lang).analyzer(s"${lang}_analyzer"))

  def multilingualField(name: String) =
    textField(name)
      .fields(
        List(
          textField("english").analyzer(englishAnalyzer.name),
          textField("shingles").analyzer(shingleAsciifoldingAnalyzer.name)
        ) ++
          languagesTextFields
      )

  def multilingualFieldWithKeyword(name: String) = textField(name).fields(
    lowercaseKeyword("keyword"),
    // we don't care about the name, we just want to compose the fields parameter
    multilingualField("").fields: _*
  )

  def lowercaseKeyword(name: String) =
    keywordField(name).normalizer(lowercaseNormalizer.name)

  def asciifoldingTextFieldWithKeyword(name: String) =
    textField(name)
      .fields(
        /**
          * Having a keyword and lowercaseKeyword allows you to
          * aggregate accurately on the field i.e. `ID123` does not become `id123`
          * but also allows you to do keyword searches e.g. `id123` matches `ID123`
          */
        keywordField("keyword"),
        lowercaseKeyword("lowercaseKeyword")
      )
      .analyzer(asciifoldingAnalyzer.name)

  val label = asciifoldingTextFieldWithKeyword("label")

  val canonicalId = lowercaseKeyword("canonicalId")

  val version = intField("version")

  val sourceIdentifierFields: Seq[KeywordField] =
    Seq(
      keywordField("identifierType.id"),
      keywordField("ontologyType"),
      lowercaseKeyword("value")
    )

  val sourceIdentifier = objectField("sourceIdentifier")
    .fields(sourceIdentifierFields)
    .dynamic("false")
}
