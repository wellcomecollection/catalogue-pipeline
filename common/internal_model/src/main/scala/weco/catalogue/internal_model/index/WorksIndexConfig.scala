package weco.catalogue.internal_model.index

import buildinfo.BuildInfo
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.fields.{
  ElasticField,
  ObjectField,
  TokenCountField
}
import com.sksamuel.elastic4s.requests.mappings.dynamictemplate.DynamicMapping
import weco.elasticsearch.{IndexConfig, RefreshInterval}

import scala.concurrent.duration.DurationInt

sealed trait WorksIndexConfig
object WorksIndexConfig extends IndexConfigFields {
  import WorksAnalysis._
  val analysis = WorksAnalysis()

  def apply(
    fields: Seq[ElasticField],
    dynamicMapping: DynamicMapping = DynamicMapping.False,
    refreshInterval: RefreshInterval = RefreshInterval.Default
  ): IndexConfig = {
    IndexConfig(
      {
        val version = BuildInfo.version.split("\\.").toList
        properties(fields)
          .dynamic(dynamicMapping)
          .meta(Map(s"model.versions.${version.head}" -> version.tail.head))
      },
      analysis
    )
  }

  val source = WorksIndexConfig(Seq.empty)
  val merged = WorksIndexConfig(
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
  )
  val identified = WorksIndexConfig(
    Seq(
      objectField("data").fields(
        objectField("otherIdentifiers").fields(lowercaseKeyword("value"))
      ),
      objectField("state")
        .fields(sourceIdentifier)
    )
  )
  val denormalised = WorksIndexConfig(Seq.empty)
  val ingested = WorksIndexConfig(
    {
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
          englishTextField("description").copyTo(relationsPath),
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

      Seq(
        state,
        search,
        keywordField("type"),
        data.withDynamic("false"),
        objectField("invisibilityReason")
          .fields(keywordField("type"))
          .withDynamic("false"),
        objectField("deletedReason")
          .fields(keywordField("type"))
          .withDynamic("false"),
        objectField("redirectTarget").withDynamic("false"),
        objectField("redirectSources").withDynamic("false"),
        version
      )
    },
    DynamicMapping.Strict,
    RefreshInterval.On(30.seconds)
  )
}
