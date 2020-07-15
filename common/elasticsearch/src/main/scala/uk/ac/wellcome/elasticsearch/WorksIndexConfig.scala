package uk.ac.wellcome.elasticsearch

import com.sksamuel.elastic4s.ElasticDsl.{
  booleanField,
  dateField,
  intField,
  keywordField,
  objectField,
  properties,
  textField,
  tokenCountField
}
import com.sksamuel.elastic4s.json.{XContentBuilder, XContentFactory}
import com.sksamuel.elastic4s.requests.analysis.{
  Analysis,
  CustomAnalyzer,
  PathHierarchyTokenizer,
  ShingleTokenFilter,
  StemmerTokenFilter,
  TokenFilter
}
import com.sksamuel.elastic4s.requests.mappings.{
  FieldDefinition,
  ObjectField,
  TextField
}
import com.sksamuel.elastic4s.requests.mappings.dynamictemplate.DynamicMapping

// TODO: Patch back to elastic4s
case class AsciiFoldingTokenFilter(override val name: String,
                                   preserveOriginal: Option[Boolean] = None)
    extends TokenFilter {

  override def build: XContentBuilder = {
    val b = XContentFactory.jsonBuilder()
    b.field("type", "asciifolding")
    preserveOriginal.foreach(b.field("preserve_original", _))
    b
  }
}

case object WorksIndexConfig extends IndexConfig {
  // Analysis
  val pathTokenizer = PathHierarchyTokenizer("path_hierarchy_tokenizer")

  val pathAnalyzer =
    CustomAnalyzer("path_hierarchy_analyzer", pathTokenizer.name, Nil, Nil)

  val asciiFoldingTokenFilter = AsciiFoldingTokenFilter(
    "asciifolding_token_filter",
    preserveOriginal = Some(true)
  )

  val shingleTokenFilter = ShingleTokenFilter(
    "shingle_token_filter",
    minShingleSize = Some(2),
    maxShingleSize = Some(4))

  val englishStemmerTokenFilter =
    StemmerTokenFilter("english_token_filter", lang = "english")

  val englishPossessiveStemmerTokenFilter =
    StemmerTokenFilter(
      "english_possessive_token_filter",
      lang = "possessive_english")

  val asciifoldingAnalyzer = CustomAnalyzer(
    "shingle_asciifolding_analyzer",
    tokenizer = "standard",
    tokenFilters =
      List("lowercase", shingleTokenFilter.name, asciiFoldingTokenFilter.name),
    charFilters = Nil
  )

  val englishAnalyzer = CustomAnalyzer(
    "english_analyzer",
    tokenizer = "standard",
    tokenFilters = List(
      "lowercase",
      asciiFoldingTokenFilter.name,
      englishStemmerTokenFilter.name,
      englishPossessiveStemmerTokenFilter.name),
    charFilters = Nil
  )

  val shingleAsciifoldingAnalyzer = CustomAnalyzer(
    "shingle_asciifolding_analyzer",
    tokenizer = "standard",
    tokenFilters =
      List("lowercase", shingleTokenFilter.name, asciiFoldingTokenFilter.name),
    charFilters = Nil
  )

  val analysis = Analysis(
    analyzers = List(
      pathAnalyzer,
      asciifoldingAnalyzer,
      shingleAsciifoldingAnalyzer,
      englishAnalyzer
    ),
    tokenFilters = List(
      asciiFoldingTokenFilter,
      shingleTokenFilter,
      englishStemmerTokenFilter,
      englishPossessiveStemmerTokenFilter),
    tokenizers = List(pathTokenizer)
  )

  // Fields
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

  val title = textField("title")
    .analyzer(asciifoldingAnalyzer.name)
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
    keywordField("ontologyType"),
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
    keywordField("ontologyType"),
    keywordField("type")
  )

  val agent = Seq(
    label,
    id(),
    keywordField("type"),
    keywordField("prefix"),
    keywordField("numeration"),
    keywordField("ontologyType")
  )

  val rootConcept = concept ++ agent ++ period

  val subject: Seq[FieldDefinition] = Seq(
    id(),
    label,
    keywordField("ontologyType"),
    objectField("concepts").fields(rootConcept)
  )

  def subjects: ObjectField = objectField("subjects").fields(subject)

  def genre(fieldName: String) = objectField(fieldName).fields(
    label,
    keywordField("ontologyType"),
    objectField("concepts").fields(rootConcept)
  )

  def labelledTextField(fieldName: String) = objectField(fieldName).fields(
    label,
    keywordField("ontologyType")
  )

  def period(fieldName: String) = labelledTextField(fieldName)

  def items(fieldName: String) = objectField(fieldName).fields(
    id(),
    location(),
    englishTextField("title"),
    keywordField("ontologyType")
  )

  val language = objectField("language").fields(
    label,
    keywordField("id"),
    keywordField("ontologyType")
  )

  val contributors = objectField("contributors").fields(
    id(),
    objectField("agent").fields(agent),
    objectField("roles").fields(
      label,
      keywordField("ontologyType")
    ),
    keywordField("ontologyType")
  )

  val production: ObjectField = objectField("production").fields(
    label,
    objectField("places").fields(place),
    objectField("agents").fields(agent),
    objectField("dates").fields(period),
    objectField("function").fields(concept),
    keywordField("ontologyType")
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
      workType,
      title,
      englishTextField("alternativeTitles"),
      englishTextField("description"),
      englishTextField("physicalDescription"),
      englishTextField("lettering"),
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
      booleanField("merged"),
      objectField("collectionPath").fields(
        label,
        objectField("level").fields(keywordField("type")),
        pathField,
        tokenCountField("depth").analyzer("standard")
      ),
      images
    )

  val fields: Seq[FieldDefinition with Product with Serializable] =
    Seq(
      canonicalId,
      keywordField("ontologyType"),
      version,
      sourceIdentifier,
      objectField("redirect")
        .fields(sourceIdentifier, canonicalId),
      keywordField("type"),
      data(analyzedPath),
      objectField("invisibilityReasons").fields(
        keywordField("type"),
        keywordField("info")
      )
    )

  val mapping = properties(fields).dynamic(DynamicMapping.Strict)

}
