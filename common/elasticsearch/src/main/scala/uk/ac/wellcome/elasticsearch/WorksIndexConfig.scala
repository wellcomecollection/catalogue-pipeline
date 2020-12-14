package uk.ac.wellcome.elasticsearch

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.requests.mappings.{
  FieldDefinition,
  ObjectField,
  TextField
}
import com.sksamuel.elastic4s.requests.mappings.dynamictemplate.DynamicMapping

trait WorksIndexConfigFields extends IndexConfigFields {

  import WorksAnalysis._

  // Fields
  def otherIdentifiers =
    objectField("otherIdentifiers")
      .fields(sourceIdentifierFields)

  def format =
    objectField("format")
      .fields(
        label,
        keywordField("id")
      )

  def title =
    asciifoldingTextFieldWithKeyword("title")
      .fields(
        keywordField("keyword"),
        textField("english").analyzer(englishAnalyzer.name),
        textField("shingles").analyzer(shingleAsciifoldingAnalyzer.name)
      )

  def notes =
    objectField("notes")
      .fields(
        keywordField("type"),
        englishTextField("content")
      )

  def period(idState: ObjectField) = Seq(
    label,
    idState,
    objectField("range").fields(
      label,
      dateField("from"),
      dateField("to"),
      booleanField("inferred")
    )
  )

  def place(idState: ObjectField) = Seq(
    label,
    idState
  )

  def concept(idState: ObjectField) = Seq(
    label,
    idState,
    keywordField("type")
  )

  def agent(idState: ObjectField) = Seq(
    label,
    idState,
    keywordField("type"),
    keywordField("prefix"),
    keywordField("numeration")
  )

  def rootConcept(idState: ObjectField) =
    concept(idState) ++ agent(idState) ++ period(idState)

  def subject(idState: ObjectField): Seq[FieldDefinition] = Seq(
    idState,
    label,
    objectField("concepts").fields(rootConcept(idState))
  )

  def subjects(idState: ObjectField): ObjectField =
    objectField("subjects").fields(subject(idState))

  def genre(fieldName: String, idState: ObjectField) =
    objectField(fieldName).fields(
      label,
      objectField("concepts").fields(rootConcept(idState))
    )

  def labelledTextField(fieldName: String) = objectField(fieldName).fields(
    label
  )

  def period(fieldName: String) = labelledTextField(fieldName)

  def items(fieldName: String, idState: ObjectField) =
    objectField(fieldName).fields(
      idState,
      location(),
      title
    )

  val languageFields: Seq[FieldDefinition] = Seq(
    label,
    keywordField("id")
  )

  def contributors(idState: ObjectField) = objectField("contributors").fields(
    idState,
    objectField("agent").fields(agent(idState)),
    objectField("roles").fields(label),
  )

  def production(idState: ObjectField): ObjectField =
    objectField("production").fields(
      label,
      objectField("places").fields(place(idState)),
      objectField("agents").fields(agent(idState)),
      objectField("dates").fields(period(idState)),
      objectField("function").fields(concept(idState))
    )

  def mergeCandidates = objectField("mergeCandidates").fields(
    objectField("identifier").fields(sourceIdentifierFields),
    keywordField("reason")
  )

  def imageData(idState: ObjectField) =
    objectField("imageData")
      .fields(
        idState,
        location("locations"),
        version
      )

  def analyzedPath: TextField =
    textField("path")
      .copyTo("data.collectionPath.depth")
      .analyzer(pathAnalyzer.name)
      .fields(keywordField("keyword"))

  def data(pathField: TextField, idState: ObjectField = id()): ObjectField =
    objectField("data").fields(
      otherIdentifiers,
      mergeCandidates,
      format,
      title,
      englishTextKeywordField("alternativeTitles"),
      englishTextField("description"),
      englishTextKeywordField("physicalDescription"),
      englishTextKeywordField("lettering"),
      objectField("createdDate").fields(period(idState)),
      contributors(idState),
      subjects(idState),
      genre("genres", idState),
      items("items", idState),
      production(idState),
      objectField("languages").fields(languageFields),
      location("thumbnail"),
      textField("edition"),
      notes,
      intField("duration"),
      collectionPath(pathField),
      imageData(idState),
      keywordField("workType")
    )

  def collectionPath(pathField: TextField) =
    objectField("collectionPath").fields(
      label,
      objectField("level").fields(keywordField("type")),
      pathField,
      tokenCountField("depth").analyzer("standard")
    )

  def relation(name: String, idState: ObjectField) =
    // Locally override the strict mapping mode. For now don't index any of the
    // relation data, as doing so stresses the ES cluster. We can map specific
    // bits of data in the future if required.
    objectField(name).dynamic("false")

  def relations(idState: ObjectField) =
    objectField("relations").fields(
      relation("ancestors", idState),
      relation("children", idState),
      relation("siblingsPreceding", idState),
      relation("siblingsSucceeding", idState),
    )

  val derivedWorkData = objectField("derivedData").fields(
    booleanField("availableOnline")
  )
}

sealed trait WorksIndexConfig extends IndexConfig with WorksIndexConfigFields {

  val analysis = WorksAnalysis()

  val dynamicMapping: DynamicMapping

  def fields: Seq[FieldDefinition with Product with Serializable]

  def mapping = properties(fields).dynamic(dynamicMapping)
}

object SourceWorkIndexConfig extends WorksIndexConfig {

  val fields = Seq.empty
  val dynamicMapping = DynamicMapping.False
}

object MergedWorkIndexConfig extends WorksIndexConfig {

  val fields = Seq.empty
  val dynamicMapping = DynamicMapping.False
}

object IdentifiedWorkIndexConfig extends WorksIndexConfig {

  val fields = Seq(
    keywordField("type"),
    objectField("data").fields(
      collectionPath(analyzedPath)
    )
  )

  val dynamicMapping = DynamicMapping.False
}

object DenormalisedWorkIndexConfig extends WorksIndexConfig {

  val fields = Seq(
    keywordField("type"),
    objectField("data").fields(
      collectionPath(analyzedPath)
    )
  )

  val dynamicMapping = DynamicMapping.False

  /** Denormalised relations make index sizes explode from about 5 GB without relations
    * to an esimated 70 GB with relations. According to elastic search documentation
    * (https://www.elastic.co/guide/en/elasticsearch/reference/current/size-your-shards.html), shards
    * sizes should be between 10 GB and 50 GB. Setting number of shards to 2 should result in shards
    * of about 35 GB.
    */
  override val shards = 2
}

object IndexedWorkIndexConfig extends WorksIndexConfig {

  val state = objectField("state").fields(
    canonicalId,
    sourceIdentifier,
    modifiedTime,
    relations(id("id")),
    derivedWorkData
  )

  val dynamicMapping = DynamicMapping.Strict

  val fields =
    Seq(
      state,
      version,
      id("redirect"),
      keywordField("type"),
      data(pathField = analyzedPath),
      objectField("invisibilityReasons").fields(
        keywordField("type"),
        keywordField("info")
      ),
      objectField("deletedReason").fields(
        keywordField("type"),
        keywordField("info")
      )
    )

  /** Denormalised relations make index sizes explode from about 5 GB without relations
    * to an esimated 70 GB with relations. According to elastic search documentation
    * (https://www.elastic.co/guide/en/elasticsearch/reference/current/size-your-shards.html), shards
    * sizes should be between 10 GB and 50 GB. Setting number of shards to 2 should result in shards
    * of about 35 GB.
    */
  override val shards = 2
}
