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
  def sourceIdentifier =
    objectField("sourceIdentifier")
      .fields(sourceIdentifierFields)

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

  def language =
    objectField("language").fields(
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

  def images(idState: ObjectField) = objectField("images").fields(
    idState,
    location("location"),
    version
  )

  def analyzedPath: TextField =
    textField("path")
      .copyTo("data.collectionPath.depth")
      .analyzer(pathAnalyzer.name)
      .fields(keywordField("keyword"))

  def data(pathField: TextField, idState: ObjectField): ObjectField =
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
      language,
      location("thumbnail"),
      textField("edition"),
      notes,
      intField("duration"),
      collectionPath(pathField),
      images(idState),
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
    objectField(name).fields(
      // Locally override the strict mapping mode. No data fields are indexed for
      // now, in the future specific fields can be added as required.
      objectField("data").dynamic("false"),
      idState,
      intField("depth")
    )

  def relations(idState: ObjectField) =
    objectField("relations").fields(
      relation("ancestors", idState),
      relation("children", idState),
      relation("siblingsPreceding", idState),
      relation("siblingsSucceeding", idState),
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

  val fields = Seq(
    keywordField("type"),
    objectField("data").fields(
      collectionPath(analyzedPath)
    )
  )

  val dynamicMapping = DynamicMapping.False
}

object DenormalisedWorkIndexConfig extends WorksIndexConfig {

  val fields = Seq.empty
  val dynamicMapping = DynamicMapping.False
}

object IdentifiedWorkIndexConfig extends WorksIndexConfig {

  val state = objectField("state").fields(
    canonicalId,
    sourceIdentifier,
    modifiedTime,
    intField("numberOfSources"),
    relations(id("id"))
  )
  
  val dynamicMapping = DynamicMapping.Strict

  val fields =
    Seq(
      state,
      version,
      id("redirect"),
      keywordField("type"),
      data(analyzedPath, id("id")),
      objectField("invisibilityReasons").fields(
        keywordField("type"),
        keywordField("info")
      )
    )
}
