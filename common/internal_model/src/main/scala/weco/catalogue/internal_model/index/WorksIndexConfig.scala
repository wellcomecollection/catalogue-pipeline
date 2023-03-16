package weco.catalogue.internal_model.index

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.fields.{ElasticField, TokenCountField}
import com.sksamuel.elastic4s.requests.mappings.dynamictemplate.DynamicMapping
import weco.elasticsearch.IndexConfig

sealed trait WorksIndexConfig
object WorksIndexConfig extends IndexConfigFields {
  import WorksAnalysis._
  val analysis = WorksAnalysis()

  def apply(
    fields: Seq[ElasticField],
    dynamicMapping: DynamicMapping = DynamicMapping.False
  ): IndexConfig =
    IndexConfig(
      properties(fields).dynamic(dynamicMapping),
      analysis
    )

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
            .analyzer(exactPathAnalyzer.name)
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
      val identifiersPath = List("query.allIdentifiers")
      val titlesAndContributorsPath = List("query.titlesAndContributors")

      // This field contains debugging information which we don't want to index, which
      // is just used by developers debugging the pipeline.
      // See dynamic-field-mapping in elasticsearch reference site: Setting the dynamic parameter to false ignores new fields
      // This means we will only index the indexedTime field within debug object for the snapshot reporter
      val debug = objectField("debug")
        .withDynamic("false")
        .fields(dateField("indexedTime"))

      // This field contains the display document used by the API, but we don't want
      // to index it -- it's just an arbitrary blob of JSON.
      val display = objectField("display").withEnabled(false)

      // This field contains the values we're actually going to query in search.
      val query = objectField("query")
        .fields(
          // top-level work
          canonicalIdField("id").copy(copyTo = identifiersPath),
          keywordField("type"),
          keywordField("format.id"),
          keywordField("workType"),
          multilingualFieldWithKeyword("title").copy(
            copyTo = titlesAndContributorsPath
          ),
          multilingualFieldWithKeyword("alternativeTitles").copy(
            copyTo = titlesAndContributorsPath
          ),
          englishTextField("description"),
          englishTextKeywordField("physicalDescription"),
          textField("edition"),
          englishTextField("notes.contents"),
          multilingualField("lettering"),
          // identifiers
          sourceIdentifierField("identifiers.value")
            .copy(copyTo = identifiersPath),
          // images
          canonicalIdField("images.id").copy(copyTo = identifiersPath),
          sourceIdentifierField("images.identifiers.value").copy(
            copyTo = identifiersPath
          ),
          // items
          canonicalIdField("items.id").copy(copyTo = identifiersPath),
          sourceIdentifierField("items.identifiers.value").copy(
            copyTo = identifiersPath
          ),
          keywordField("items.locations.accessConditions.status.id"),
          keywordField("items.locations.license.id"),
          keywordField("items.locations.locationType.id"),
          // subjects
          canonicalIdField("subjects.id"),
          labelField("subjects.label"),
          labelField("subjects.concepts.label"),
          // genres
          labelField("genres.label"),
          labelField("genres.concepts.label"),
          keywordField("genres.concepts.id"),
          // languages
          keywordField("languages.id"),
          labelField("languages.label"),
          // contributors
          canonicalIdField("contributors.agent.id"),
          labelField("contributors.agent.label").copy(
            copyTo = titlesAndContributorsPath
          ),
          // production events
          labelField("production.label"),
          dateField("production.dates.range.from"),
          // relations
          canonicalIdField("partOf.id"),
          multilingualFieldWithKeyword("partOf.title"),
          // availabilities
          keywordField("availabilities.id"),
          // collection path
          textField("collectionPath.path")
            .analyzer(exactPathAnalyzer.name)
            .fields(
              keywordField("keyword"),
              textField("clean").analyzer(cleanPathAnalyzer.name)
            ),
          textField("collectionPath.label")
            .analyzer(asciifoldingAnalyzer.name)
            .fields(
              keywordField("keyword"),
              lowercaseKeyword("lowercaseKeyword"),
              textField("cleanPath").analyzer(cleanPathAnalyzer.name),
              textField("path").analyzer(exactPathAnalyzer.name)
            ),
          // reference number
          keywordField("referenceNumber").copy(copyTo = identifiersPath),
          // fields populated by copyTo
          lowercaseKeyword("allIdentifiers"),
          multilingualField("titlesAndContributors")
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
          keywordField("availabilities")
        )

      Seq(
        keywordField("type"),
        objectField("redirectTarget").withDynamic("false"),
        debug,
        display,
        query,
        aggregatableValues
      )
    },
    DynamicMapping.Strict
  )
}
