package uk.ac.wellcome.models.index

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.fields.{KeywordField, TextField}
import uk.ac.wellcome.models.index.WorksAnalysis._

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

  def asciifoldingTextFieldWithKeyword(
    name: String,
    setEagerGlobalOrdinals: Boolean = false
  ) = {
    // We set eagerGlobalOrdinals on fields that we know we are aggregating on the frontend
    val keyword: KeywordField = setEagerGlobalOrdinals match {
      case true =>
        KeywordField("keyword", eagerGlobalOrdinals = Some(true))
      case false => keywordField("keyword")
    }

    textField(name)
      .fields(
        /**
          * Having a keyword and lowercaseKeyword allows you to
          * aggregate accurately on the field i.e. `ID123` does not become `id123`
          * but also allows you to do keyword searches e.g. `id123` matches `ID123`
          */
        keyword,
        lowercaseKeyword("lowercaseKeyword")
      )
      .analyzer(asciifoldingAnalyzer.name)
  }

  val label = asciifoldingTextFieldWithKeyword("label")

  val eagerGlobalOrdinalsLabel =
    asciifoldingTextFieldWithKeyword("label", setEagerGlobalOrdinals = true)

  val canonicalId = lowercaseKeyword("canonicalId")

  val version = intField("version")

  val sourceIdentifier = objectField("sourceIdentifier")
    .fields(lowercaseKeyword("value"))
    .withDynamic("false")
}
