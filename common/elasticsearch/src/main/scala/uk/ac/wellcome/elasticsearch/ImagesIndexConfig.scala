package uk.ac.wellcome.elasticsearch

import com.sksamuel.elastic4s.ElasticDsl.{keywordField, _}
import com.sksamuel.elastic4s.analysis.Analysis
import com.sksamuel.elastic4s.requests.mappings.{MappingDefinition, ObjectField}
import com.sksamuel.elastic4s.requests.mappings.dynamictemplate.DynamicMapping

object AugmentedImageIndexConfig extends IndexConfig {

  val analysis: Analysis = WorksAnalysis()

  def mapping: MappingDefinition = properties(Seq())
}

object ImagesIndexConfig extends IndexConfig with IndexConfigFields {

  val analysis: Analysis = WorksAnalysis()

  val inferredData = objectField("inferredData").fields(
    denseVectorField("features1", 2048),
    denseVectorField("features2", 2048),
    keywordField("lshEncodedFeatures"),
    keywordField("palette")
  )

  def sourceWork(fieldName: String): ObjectField =
    objectField(fieldName)
      .fields(
        objectField("id").fields(canonicalId, sourceIdentifier),
        objectField("data").fields(
          objectField("otherIdentifiers").fields(lowercaseKeyword("value")),
          englishTextKeywordField("title"),
          englishTextKeywordField("alternativeTitles"),
          englishTextField("description"),
          englishTextKeywordField("physicalDescription"),
          objectField("contributors").fields(
            objectField("agent").fields(label),
          ),
          objectField("subjects").fields(
            objectField("concepts").fields(label)
          ),
          objectField("genres").fields(
            objectField("concepts").fields(label)
          ),
          objectField("production").fields(
            objectField("places").fields(label),
            objectField("agents").fields(label),
            objectField("dates").fields(label),
            objectField("function").fields(label)
          ),
          objectField("languages").fields(
            label,
            keywordField("id")
          ),
          textField("edition"),
          objectField("notes").fields(englishTextField("content")),
          objectField("collectionPath").fields(label, textField("path")),
        ),
        keywordField("type"),
      )
      .dynamic("false")

  val source = objectField("source").fields(
    sourceWork("canonicalWork"),
    sourceWork("redirectedWork"),
    keywordField("type")
  )

  val state = objectField("state")
    .fields(
      canonicalId,
      sourceIdentifier,
      inferredData,
      objectField("derivedData").dynamic("false")
    )

  val fields = Seq(
    version,
    dateField("modifiedTime"),
    source,
    state,
    objectField("locations")
      .fields(
        objectField("license").fields(keywordField("id")),
      )
      .dynamic("false")
  )

  // Here we set dynamic strict to be sure the object vaguely looks like an
  // image and contains the core fields, adding DynamicMapping.False in places
  // where we do not need to map every field and can save CPU.
  def mapping: MappingDefinition =
    properties(fields).dynamic(DynamicMapping.Strict)
}
