package uk.ac.wellcome.models.index

import buildinfo.BuildInfo
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.requests.mappings.dynamictemplate.DynamicMapping
import com.sksamuel.elastic4s.requests.mappings.{FieldDefinition, ObjectField}
import uk.ac.wellcome.elasticsearch.IndexConfig

sealed trait WorksIndexConfig extends IndexConfig with IndexConfigFields {

  val analysis = WorksAnalysis()

  val dynamicMapping: DynamicMapping = DynamicMapping.False

  def fields: Seq[FieldDefinition with Product with Serializable]

  def mapping = {
    val version = BuildInfo.version.split("\\.").toList
    properties(fields)
      .dynamic(dynamicMapping)
      .meta(Map(s"model.versions.${version.head}" -> version.tail.head))
  }
}

object SourceWorkIndexConfig extends WorksIndexConfig {

  val fields = Seq.empty
}

object MergedWorkIndexConfig extends WorksIndexConfig {

  import WorksAnalysis._

  val fields =
    Seq(
      keywordField("type"),
      objectField("data").fields(
        objectField("collectionPath").fields(
          textField("path")
            .copyTo("data.collectionPath.depth")
            .analyzer(pathAnalyzer.name)
            .fields(lowercaseKeyword("keyword")),
          tokenCountField("depth").analyzer("standard")
        )
      )
    )
}

object IdentifiedWorkIndexConfig extends WorksIndexConfig {

  // These are not necessary for the services but are useful for debugging.
  // Because works in the source index don't have a canonical id, they are
  // indexed with the sourceIdentifier as an id. If we want to look them up
  // on the identifier index, we need to be able to search
  // by sourceIdentifier (and otherIdentifiers sometimes)
  val fields =
    Seq(
      objectField("data").fields(
        objectField("otherIdentifiers").fields(lowercaseKeyword("value"))
      ),
      objectField("state")
        .fields(sourceIdentifier)
    )
}

object DenormalisedWorkIndexConfig extends WorksIndexConfig {

  val fields = Seq.empty
}

object IndexedWorkIndexConfig extends WorksIndexConfig {

  import uk.ac.wellcome.models.index.WorksAnalysis._

  // Here we set dynamic strict to be sure the object vaguely looks like a work
  // and contains the core fields, adding DynamicMapping.False in places where
  // we do not need to map every field and can save CPU.
  override val dynamicMapping: DynamicMapping = DynamicMapping.Strict

  // Indexing lots of individual fields on Elasticsearch can be very CPU
  // intensive, so here only include fields that are needed for querying in the
  // API.
  def data: ObjectField =
    objectField("data").fields(
      objectField("otherIdentifiers").fields(lowercaseKeyword("value")),
      objectField("format").fields(keywordField("id")),
      multilingualFieldWithKeyword("title"),
      multilingualFieldWithKeyword("alternativeTitles"),
      englishTextField("description"),
      englishTextKeywordField("physicalDescription"),
      multilingualField("lettering"),
      objectField("contributors").fields(
        objectField("agent").fields(label)
      ),
      objectField("subjects").fields(
        label,
        objectField("concepts").fields(label)
      ),
      objectField("genres").fields(
        label,
        objectField("concepts").fields(label)
      ),
      objectField("items").fields(
        objectField("locations").fields(
          keywordField("type"),
          objectField("locationType").fields(keywordField("id")),
          objectField("license").fields(keywordField("id")),
          objectField("accessConditions").fields(
            objectField("status").fields(keywordField("type"))
          )
        ),
        objectField("id").fields(
          canonicalId,
          sourceIdentifier,
          objectField("otherIdentifiers").fields(lowercaseKeyword("value"))
        )
      ),
      objectField("production").fields(
        label,
        objectField("places").fields(label),
        objectField("agents").fields(label),
        objectField("dates").fields(
          label,
          objectField("range").fields(dateField("from"))
        ),
        objectField("function").fields(label)
      ),
      objectField("languages").fields(label, keywordField("id")),
      textField("edition"),
      objectField("notes").fields(englishTextField("content")),
      intField("duration"),
      collectionPath(copyDepthTo = Some("data.collectionPath.depth")),
      objectField("imageData").fields(
        objectField("id").fields(canonicalId, sourceIdentifier)
      ),
      keywordField("workType")
    )

  def collectionPath(copyDepthTo: Option[String]) = {
    val path = textField("path")
      .analyzer(pathAnalyzer.name)
      .fields(keywordField("keyword"))
      .copyTo(copyDepthTo.toList)

    objectField("collectionPath").fields(
      label,
      path,
      tokenCountField("depth").analyzer("standard")
    )
  }

  val state = objectField("state")
    .fields(
      canonicalId,
      sourceIdentifier,
      dateField("sourceModifiedTime"),
      dateField("mergedTime"),
      dateField("indexedTime"),
      objectField("availabilities").fields(keywordField("id")),
      objectField("relations")
        .fields(
          objectField("ancestors").fields(
            lowercaseKeyword("id"),
            intField("depth"),
            intField("numChildren"),
            intField("numDescendents"),
            multilingualFieldWithKeyword("title"),
            collectionPath(copyDepthTo = None)
          )
        )
        .dynamic("false"),
      objectField("derivedData")
        .fields(
          keywordField("contributorAgents")
        )
        .dynamic("false")
    )

  val fields =
    Seq(
      state,
      keywordField("type"),
      data.dynamic("false"),
      objectField("invisibilityReasons")
        .fields(keywordField("type"))
        .dynamic("false"),
      objectField("deletedReason")
        .fields(keywordField("type"))
        .dynamic("false"),
      objectField("redirectTarget").dynamic("false"),
      objectField("redirectSources").dynamic(false),
      version
    )
}
