package weco.catalogue.internal_model.index

import buildinfo.BuildInfo
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.fields.{
  ElasticField,
  KeywordField,
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
      //
      // We copy the collectionPath field to enable two types of query in the
      // relation embedder:
      //
      //    - we can search for parts of a path
      //      e.g. if we had the path "PP/CRI/1/2", we could find this work by
      //      searching "PP/CRI" or "PP/CRI/1"
      //
      //    - we can search by the depth of a path
      //      e.g. if we had the path "PP/CRI/1/2", we could find this work by
      //      looking for works with relationPath.depth = 4
      //
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
  val indexed = WorksIndexConfig(
    {
      val relationsPath = List("search.relations")
      val identifiersPath = List("search.identifiers")
      val titlesAndContributorsPath = List("search.titlesAndContributors")

      // Indexing lots of individual fields on Elasticsearch can be very CPU
      // intensive, so here only include fields that are needed for querying in the
      // API.
      def data: ObjectField =
        objectField("data").fields(
          objectField("otherIdentifiers")
            .fields(lowercaseKeyword("value").copy(copyTo = identifiersPath)),
          objectField("format").fields(keywordField("id")),
          multilingualFieldWithKeyword("title")
            .copyTo(relationsPath ++ titlesAndContributorsPath),
          multilingualFieldWithKeyword("alternativeTitles")
            .copyTo(relationsPath ++ titlesAndContributorsPath),
          englishTextField("description").copyTo(relationsPath),
          englishTextKeywordField("physicalDescription"),
          multilingualField("lettering"),
          objectField("contributors").fields(
            objectField("agent")
              .fields(label.copyTo(titlesAndContributorsPath))
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
              canonicalId.copy(copyTo = identifiersPath),
              objectField("sourceIdentifier")
                .fields(
                  lowercaseKeyword("value").copy(copyTo = identifiersPath)
                )
                .withDynamic("false"),
              objectField("otherIdentifiers")
                .fields(
                  lowercaseKeyword("value").copy(copyTo = identifiersPath)
                )
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
          objectField("notes").fields(englishTextField("contents")),
          intField("duration"),
          collectionPath(copyPathTo = Some("data.collectionPath.depth")),
          objectField("imageData").fields(
            objectField("id").fields(
              canonicalId.copy(copyTo = identifiersPath),
              objectField("sourceIdentifier")
                .fields(
                  lowercaseKeyword("value").copy(copyTo = identifiersPath)
                )
                .withDynamic("false")
            )
          ),
          keywordField("workType"),
          keywordField("referenceNumber").copy(copyTo = identifiersPath)
        )

      // We copy the collectionPath label and the tokenized fields into the
      // search.relations field for two reasons:
      //
      //    - to enable searching for parts of a path
      //      e.g. if we had the path "PP/CRI/1/2", we could find this work by
      //      searching "PP/CRI" or "PP/CRI/1"
      //
      //    - so we can boost exact matches for the label
      //      e.g. if a user searches for "SAFPA/1/2", we want to prioritise the
      //      work where that appears as the referenceNumber, even if other works
      //      mention this reference in other fields
      //
      // TODO: Do we need to copy the depth here?  We need this for the relation
      // embedder, but it's not clear if it's used in the API.
      //
      def collectionPath(copyPathTo: Option[String]): ObjectField = {
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
          canonicalId.copy(copyTo = identifiersPath),
          objectField("sourceIdentifier")
            .fields(
              lowercaseKeyword("value").copy(copyTo = identifiersPath)
            )
            .withDynamic("false"),
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
            .withDynamic("false")
        )

      val search = objectField("search").fields(
        textField("identifiers").analyzer("whitespace_analyzer"),
        textField("relations").analyzer("with_slashes_text_analyzer"),
        multilingualField("titlesAndContributors")
      )

      // This field contains debugging information which we don't want to index, which
      // is just used by developers debugging the pipeline.
      val debug = objectField("debug").withEnabled(false)

      // This field contains the display document used by the API, but we don't want
      // to index it -- it's just an arbitrary blob of JSON.
      val display = objectField("display").withEnabled(false)

      // This field contains the values we're actually going to query in search.
      val query = objectField("query")
        .fields(
          // top-level work
          canonicalIdField("id"),
          sourceIdentifierField("identifiers.value"),
          // images
          canonicalIdField("images.id"),
          sourceIdentifierField("images.identifiers.value"),
          // items
          canonicalIdField("items.id"),
          sourceIdentifierField("items.identifiers.value"),
          // subjects
          canonicalIdField("subjects.id"),
          sourceIdentifierField("subjects.identifiers.value"),
          keywordField("subjects.label"),
          asciifoldingTextFieldWithKeyword("subjects.concepts.label"),
          // genres
          asciifoldingTextFieldWithKeyword("genres.concepts.label"),
          // relations
          canonicalIdField("partOf.id"),
          multilingualFieldWithKeyword("partOf.title"),
          // availabilities
          keywordField("availabilities.id")
        )

      // This field contains the display documents used by aggregations.
      //
      // The values are JSON documents that can be included in an AggregationBucket from
      // the API, but those documents are then encoded as JSON strings so they can be
      // returned as single values from an Elasticsearch terms aggregation.
      //
      // See https://github.com/wellcomecollection/docs/tree/main/rfcs/049-catalogue-api-aggregations-modelling
      val aggregatableValues = objectField("aggregatableValues")
        .fields(
          keywordField("workType"),
          keywordField("genres.label"),
          keywordField("production.dates"),
          keywordField("subjects.label"),
          keywordField("languages"),
          keywordField("contributors.agent.label"),
          keywordField("items.locations.license"),
          keywordField("availabilities"),
        )

      Seq(
        state,
        search,
        keywordField("type"),
        data.withDynamic("false"),
        objectField("redirectTarget").withDynamic("false"),
        debug,
        display,
        query,
        aggregatableValues
      )
    },
    DynamicMapping.Strict,
    RefreshInterval.On(30.seconds)
  )

  private def canonicalIdField(name: String): KeywordField =
    lowercaseKeyword(name)
  private def sourceIdentifierField(name: String): KeywordField =
    lowercaseKeyword(name)
}
