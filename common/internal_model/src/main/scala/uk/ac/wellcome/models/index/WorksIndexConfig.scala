package uk.ac.wellcome.models.index

import buildinfo.BuildInfo
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.fields.{
  ElasticField,
  ObjectField,
  TokenCountField
}
import com.sksamuel.elastic4s.requests.mappings.dynamictemplate.DynamicMapping
import uk.ac.wellcome.elasticsearch.{IndexConfig, RefreshInterval}

import scala.concurrent.duration.DurationInt

sealed trait WorksIndexConfig extends IndexConfig with IndexConfigFields {

  val analysis = WorksAnalysis()

  val dynamicMapping: DynamicMapping = DynamicMapping.False

  def fields: Seq[ElasticField]

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
          TokenCountField("depth").withAnalyzer("standard")
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

  val relationsPath = List("search.relations")
  val titlesAndContributorsPath = List("search.titlesAndContributors")

  // Indexing lots of individual fields on Elasticsearch can be very CPU
  // intensive, so here only include fields that are needed for querying in the
  // API.
  def data: ObjectField =
    objectField("data").fields(
      objectField("otherIdentifiers").fields(lowercaseKeyword("value")),
      objectField("format").fields(keywordField("id")),
      multilingualFieldWithKeyword("title")
        .copyTo(relationsPath ++ titlesAndContributorsPath),
      multilingualFieldWithKeyword("alternativeTitles")
        .copyTo(relationsPath ++ titlesAndContributorsPath),
      englishTextField("description"),
      englishTextKeywordField("physicalDescription"),
      multilingualField("lettering"),
      objectField("contributors").fields(
        objectField("agent").fields(label.copyTo(titlesAndContributorsPath))
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
      collectionPath(copyPathTo = Some("data.collectionPath.depth")),
      objectField("imageData").fields(
        objectField("id").fields(canonicalId, sourceIdentifier)
      ),
      keywordField("workType")
    )

  def collectionPath(copyPathTo: Option[String]) = {
    val path = textField("path")
      .analyzer(pathAnalyzer.name)
      .fields(keywordField("keyword"))
      .copyTo(copyPathTo.toList ++ relationsPath)

    objectField("collectionPath").fields(
      label.copyTo(relationsPath),
      path,
      TokenCountField("depth").withAnalyzer("standard")
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
            multilingualFieldWithKeyword("title").copyTo(relationsPath),
            collectionPath(copyPathTo = None)
          )
        )
        .withDynamic("false"),
      objectField("derivedData")
        .fields(
          keywordField("contributorAgents")
        )
        .withDynamic("false")
    )

  val search = objectField("search").fields(
    textField("relations").analyzer("with_slashes_text_analyzer"),
    multilingualField("titlesAndContributors")
  )

  val fields =
    Seq(
      state,
      search,
      keywordField("type"),
      data.withDynamic("false"),
      objectField("invisibilityReasons")
        .fields(keywordField("type"))
        .withDynamic("false"),
      objectField("deletedReason")
        .fields(keywordField("type"))
        .withDynamic("false"),
      objectField("redirectTarget").withDynamic("false"),
      objectField("redirectSources").withDynamic("false"),
      version
    )

  override val refreshInterval = RefreshInterval.On(30.seconds)
}
