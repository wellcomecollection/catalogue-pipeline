package uk.ac.wellcome.elasticsearch

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.requests.mappings.{
  FieldDefinition,
  ObjectField,
  TextField
}
import com.sksamuel.elastic4s.requests.mappings.dynamictemplate.DynamicMapping

sealed trait WorksIndexConfig extends IndexConfig {

  import WorksAnalysis._
  val analysis = WorksAnalysis()

  def state: ObjectField

  // Fields
  val sourceIdentifier = objectField("sourceIdentifier")
    .fields(sourceIdentifierFields)

  val otherIdentifiers = objectField("otherIdentifiers")
    .fields(sourceIdentifierFields)

  val format = objectField("format")
    .fields(
      label,
      keywordField("id")
    )

  val title = asciifoldingTextFieldWithKeyword("title")
    .fields(
      keywordField("keyword"),
      textField("english").analyzer(englishAnalyzer.name),
      textField("shingles").analyzer(shingleAsciifoldingAnalyzer.name)
    )

  val notes = objectField("notes")
    .fields(
      keywordField("type"),
      englishTextField("content")
    )

  val period = Seq(
    label,
    id(),
    objectField("range").fields(
      label,
      dateField("from"),
      dateField("to"),
      booleanField("inferred")
    )
  )

  val place = Seq(
    label,
    id()
  )

  val concept = Seq(
    label,
    id(),
    keywordField("type")
  )

  val agent = Seq(
    label,
    id(),
    keywordField("type"),
    keywordField("prefix"),
    keywordField("numeration")
  )

  val rootConcept = concept ++ agent ++ period

  val subject: Seq[FieldDefinition] = Seq(
    id(),
    label,
    objectField("concepts").fields(rootConcept)
  )

  def subjects: ObjectField = objectField("subjects").fields(subject)

  def genre(fieldName: String) = objectField(fieldName).fields(
    label,
    objectField("concepts").fields(rootConcept)
  )

  def labelledTextField(fieldName: String) = objectField(fieldName).fields(
    label
  )

  def period(fieldName: String) = labelledTextField(fieldName)

  def items(fieldName: String) = objectField(fieldName).fields(
    id(),
    location(),
    title
  )

  val language = objectField("language").fields(
    label,
    keywordField("id")
  )

  val contributors = objectField("contributors").fields(
    id(),
    objectField("agent").fields(agent),
    objectField("roles").fields(label),
  )

  val production: ObjectField = objectField("production").fields(
    label,
    objectField("places").fields(place),
    objectField("agents").fields(agent),
    objectField("dates").fields(period),
    objectField("function").fields(concept)
  )

  val mergeCandidates = objectField("mergeCandidates").fields(
    objectField("identifier").fields(sourceIdentifierFields),
    keywordField("reason")
  )

  val images = objectField("images").fields(
    id(),
    location("location"),
    version
  )

  private val analyzedPath: TextField = textField("path")
    .copyTo("data.collectionPath.depth")
    .analyzer(pathAnalyzer.name)
    .fields(keywordField("keyword"))

  def data(pathField: TextField): ObjectField =
    objectField("data").fields(
      otherIdentifiers,
      mergeCandidates,
      format,
      title,
      englishTextKeywordField("alternativeTitles"),
      englishTextField("description"),
      englishTextKeywordField("physicalDescription"),
      englishTextKeywordField("lettering"),
      objectField("createdDate").fields(period),
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
      objectField("collectionPath").fields(
        label,
        objectField("level").fields(keywordField("type")),
        pathField,
        tokenCountField("depth").analyzer("standard")
      ),
      images,
      keywordField("workType")
    )

  def relation(name: String) = objectField(name).fields(
    // Locally override the strict mapping mode. No data fields are indexed for
    // now, in the future specific fields can be added as required.
    objectField("data").dynamic("false"),
    id(),
    intField("depth")
  )

  val relations = objectField("relations").fields(
    relation("ancestors"),
    relation("children"),
    relation("siblingsPreceding"),
    relation("siblingsSucceeding"),
  )

  def fields: Seq[FieldDefinition with Product with Serializable] =
    Seq(
      state,
      version,
      objectField("redirect")
        .fields(sourceIdentifier, canonicalId, otherIdentifiers),
      keywordField("type"),
      data(analyzedPath),
      objectField("invisibilityReasons").fields(
        keywordField("type"),
        keywordField("info")
      )
    )

  def mapping = properties(fields).dynamic(DynamicMapping.Strict)
}

object SourceWorkIndexConfig extends WorksIndexConfig {

  val state = objectField("state").fields(sourceIdentifier)
}

object MergedWorkIndexConfig extends WorksIndexConfig {

  val state = objectField("state").fields(
    sourceIdentifier,
    booleanField("hasMultipleSources"),
  )
}

object DenormalisedWorkIndexConfig extends WorksIndexConfig {

  val state = objectField("state").fields(
    sourceIdentifier,
    booleanField("hasMultipleSources"),
    relations
  )
}

object IdentifiedWorkIndexConfig extends WorksIndexConfig {

  val state = objectField("state").fields(
    canonicalId,
    sourceIdentifier,
    booleanField("hasMultipleSources"),
    relations
  )
}
