package weco.catalogue.internal_model.index

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.fields.{
  IntegerField,
  KeywordField,
  ObjectField,
  TextField
}
import weco.catalogue.internal_model.index.WorksAnalysis._
import weco.elasticsearch.ElasticFieldOps

/** Mixin for common fields used within an IndexConfig in our internal models.
  */
trait IndexConfigFields extends ElasticFieldOps {

  def textKeywordField(name: String,
                       textFieldName: String,
                       analyzerName: String): TextField =
    textField(name).fields(
      keywordField("keyword"),
      textField(textFieldName).analyzer(analyzerName)
    )

  def englishTextKeywordField(name: String): TextField =
    textKeywordField(
      name = name,
      textFieldName = "english",
      analyzerName = englishAnalyzer.name)

  def englishTextField(name: String): TextField =
    textField(name).fields(
      textField("english").analyzer(englishAnalyzer.name)
    )

  val languagesTextFields: List[TextField] =
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

  def multilingualFieldWithKeyword(name: String): TextField =
    textField(name).fields(
      Seq(lowercaseKeyword("keyword")) ++
        // we don't care about the name, we just want to compose the fields parameter
        multilingualField("").fields
    )

  def lowercaseKeyword(name: String): KeywordField =
    keywordField(name).normalizer(lowercaseNormalizer.name)

  def asciifoldingTextFieldWithKeyword(name: String): TextField =
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

  val label: TextField = labelField(name = "label")

  val canonicalId: KeywordField = lowercaseKeyword("canonicalId")

  val version: IntegerField = intField("version")

  val sourceIdentifier: ObjectField =
    objectField("sourceIdentifier")
      .fields(lowercaseKeyword("value"))
      .withDynamic("false")
}
