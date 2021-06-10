package uk.ac.wellcome.models.index

import buildinfo.BuildInfo
import com.sksamuel.elastic4s.ElasticDsl.{keywordField, _}
import com.sksamuel.elastic4s.analysis.Analysis
import com.sksamuel.elastic4s.fields.{DenseVectorField, ObjectField}
import com.sksamuel.elastic4s.requests.mappings.MappingDefinition
import com.sksamuel.elastic4s.requests.mappings.dynamictemplate.DynamicMapping
import uk.ac.wellcome.elasticsearch.IndexConfig

object InitialImageIndexConfig extends IndexConfig {

  val analysis: Analysis = WorksAnalysis()

  def mapping: MappingDefinition =
    properties(Seq()).dynamic(DynamicMapping.False)
}

object AugmentedImageIndexConfig extends IndexConfig {

  val analysis: Analysis = WorksAnalysis()

  def mapping: MappingDefinition =
    properties(Seq()).dynamic(DynamicMapping.False)
}

object IndexedImageIndexConfig extends IndexConfig with IndexConfigFields {

  val analysis: Analysis = WorksAnalysis()

  val inferredData = objectField("inferredData").fields(
    DenseVectorField("features1", dims = 2048),
    DenseVectorField("features2", dims = 2048),
    keywordField("lshEncodedFeatures"),
    keywordField("palette"),
    intField("binSizes").withIndex(false),
    floatField("binMinima").withIndex(false),
    floatField("aspectRatio")
  )

  def sourceWork(fieldName: String): ObjectField =
    objectField(fieldName)
      .fields(
        objectField("id").fields(canonicalId, sourceIdentifier),
        objectField("data").fields(
          objectField("otherIdentifiers").fields(lowercaseKeyword("value")),
          multilingualFieldWithKeyword("title"),
          multilingualFieldWithKeyword("alternativeTitles"),
          multilingualField("description"),
          englishTextKeywordField("physicalDescription"),
          multilingualField("lettering"),
          objectField("contributors").fields(
            objectField("agent").fields(label)
          ),
          objectField("subjects").fields(
            objectField("concepts").fields(label)
          ),
          objectField("genres").fields(
            label,
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
          objectField("notes").fields(multilingualField("content")),
          objectField("collectionPath").fields(label, textField("path"))
        ),
        keywordField("type")
      )
      .withDynamic("false")

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
      objectField("derivedData")
        .fields(
          keywordField("sourceContributorAgents")
        )
        .withDynamic("false")
    )

  val fields = Seq(
    version,
    dateField("modifiedTime"),
    source,
    state,
    objectField("locations")
      .fields(
        objectField("license").fields(keywordField("id"))
      )
      .withDynamic("false")
  )

  // Here we set dynamic strict to be sure the object vaguely looks like an
  // image and contains the core fields, adding DynamicMapping.False in places
  // where we do not need to map every field and can save CPU.
  def mapping = {
    val version = BuildInfo.version.split("\\.").toList
    properties(fields)
      .dynamic(DynamicMapping.Strict)
      .meta(Map(s"model.versions.${version.head}" -> version.tail.head))
  }
}
