package uk.ac.wellcome.elasticsearch

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.requests.analysis.{Analysis, CustomNormalizer}
import com.sksamuel.elastic4s.requests.analyzers.LowercaseTokenFilter
import com.sksamuel.elastic4s.requests.mappings.{FieldDefinition, ObjectField}

object WorksIndex {
  val lowercaseNormalizer = CustomNormalizer(
    "lowercase",
    tokenFilters = List(LowercaseTokenFilter.name),
    charFilters = List()
  )

  val value = keywordField("value")
    .normalizer(lowercaseNormalizer.name)
    .fields(keywordField("raw"))

  val canonicalId =
    keywordField("canonicalId")
      .normalizer(lowercaseNormalizer.name)
      .fields(keywordField("raw"))

  val label = textField("label").fields(keywordField("raw"))
  val license = objectField("license").fields(
    keywordField("id")
  )

  def sourceIdentifierFields = Seq(
    keywordField("ontologyType"),
    objectField("identifierType").fields(
      label,
      keywordField("id"),
      keywordField("ontologyType")
    ),
    value
  )

  val sourceIdentifier = objectField("sourceIdentifier")
    .fields(sourceIdentifierFields)

  val otherIdentifiers = objectField("otherIdentifiers")
    .fields(sourceIdentifierFields)

  val workType = objectField("workType")
    .fields(
      label,
      keywordField("ontologyType"),
      keywordField("id")
    )

  val notes = objectField("notes")
    .fields(
      keywordField("type"),
      englishTextField("content")
    )

  def location(fieldName: String = "locations") =
    objectField(fieldName).fields(
      keywordField("type"),
      keywordField("ontologyType"),
      objectField("locationType").fields(
        label,
        keywordField("id"),
        keywordField("ontologyType")
      ),
      label,
      textField("url"),
      textField("credit"),
      license
    )

  def date(fieldName: String) = objectField(fieldName).fields(period)

  val period = Seq(
    label,
    keywordField("ontologyType"),
    objectField("range").fields(
      label,
      dateField("from"),
      dateField("to"),
      booleanField("inferred")
    )
  )

  val concept = Seq(
    label,
    keywordField("ontologyType"),
    keywordField("type")
  )

  val agent = Seq(
    label,
    keywordField("type"),
    keywordField("prefix"),
    keywordField("numeration"),
    keywordField("ontologyType")
  )

  val rootConcept = concept ++ agent ++ period

  def identified(fieldName: String, fields: Seq[FieldDefinition]): ObjectField =
    objectField(fieldName).fields(
      canonicalId,
      textField("type"),
      objectField("agent").fields(fields),
      objectField("sourceIdentifier").fields(sourceIdentifierFields),
      objectField("otherIdentifiers").fields(sourceIdentifierFields)
    )

  val subject: Seq[FieldDefinition] = Seq(
    label,
    keywordField("ontologyType"),
    identified("concepts", rootConcept)
  )

  def subjects: ObjectField = identified("subjects", subject)

  def genre(fieldName: String) = objectField(fieldName).fields(
    label,
    keywordField("ontologyType"),
    identified("concepts", rootConcept)
  )

  def labelledTextField(fieldName: String) = objectField(fieldName).fields(
    label,
    keywordField("ontologyType")
  )

  def period(fieldName: String) = labelledTextField(fieldName)

  def items(fieldName: String) = objectField(fieldName).fields(
    canonicalId,
    sourceIdentifier,
    otherIdentifiers,
    keywordField("type"),
    objectField("agent").fields(
      location(),
      englishTextField("title"),
      keywordField("ontologyType")
    )
  )

  def englishTextField(name: String) =
    textField(name).fields(textField("english").analyzer("english"))

  val language = objectField("language").fields(
    label,
    keywordField("id"),
    keywordField("ontologyType")
  )

  val contributors = objectField("contributors").fields(
    identified("agent", agent),
    objectField("roles").fields(
      label,
      keywordField("ontologyType")
    ),
    keywordField("ontologyType")
  )

  val production: ObjectField = objectField("production").fields(
    label,
    period("places"),
    identified("agents", agent),
    date("dates"),
    objectField("function").fields(concept),
    keywordField("ontologyType")
  )

  val mergeCandidates = objectField("mergeCandidates").fields(
    objectField("identifier").fields(sourceIdentifierFields),
    keywordField("reason")
  )

  val data: ObjectField =
    objectField("data").fields(
      otherIdentifiers,
      mergeCandidates,
      workType,
      englishTextField("title"),
      englishTextField("alternativeTitles"),
      englishTextField("description"),
      englishTextField("physicalDescription"),
      englishTextField("lettering"),
      date("createdDate"),
      contributors,
      subjects,
      genre("genres"),
      items("items"),
      production,
      language,
      location("thumbnail"),
      textField("edition"),
      notes,
      intField("duration"),
      booleanField("merged")
    )

  val rootIndexFields: Seq[FieldDefinition with Product with Serializable] =
    Seq(
      canonicalId,
      keywordField("ontologyType"),
      intField("version"),
      sourceIdentifier,
      objectField("redirect")
        .fields(sourceIdentifier, canonicalId),
      keywordField("type"),
      data
    )

  val analysis =
    Analysis(analyzers = List(), normalizers = List(lowercaseNormalizer))
}
