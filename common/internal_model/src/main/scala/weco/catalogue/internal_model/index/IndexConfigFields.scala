package weco.catalogue.internal_model.index

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.fields.{KeywordField, TextField}
import weco.catalogue.internal_model.index.WorksAnalysis._
import weco.elasticsearch.ElasticFieldOps

/** Mixin for common fields used within an IndexConfig in our internal models.
  */
trait IndexConfigFields extends ElasticFieldOps {
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

  def multilingualField(name: String): TextField =
    textField(name)
      .fields(
        List(
          textField("english").analyzer(englishAnalyzer.name),
          textField("shingles").analyzer(shingleAsciifoldingAnalyzer.name)
        ) ++
          languagesTextFields
      )

  def multilingualFieldWithKeyword(name: String) = textField(name).fields(
    Seq(lowercaseKeyword("keyword")) ++
      // we don't care about the name, we just want to compose the fields parameter
      multilingualField("").fields
  )

  def lowercaseKeyword(name: String): KeywordField =
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

  def labelField(name: String): TextField =
    asciifoldingTextFieldWithKeyword(name)

  val label = labelField(name = "label")

  val canonicalId = lowercaseKeyword("canonicalId")

  val version = intField("version")

  val sourceIdentifier = objectField("sourceIdentifier")
    .fields(lowercaseKeyword("value"))
    .withDynamic("false")
}
