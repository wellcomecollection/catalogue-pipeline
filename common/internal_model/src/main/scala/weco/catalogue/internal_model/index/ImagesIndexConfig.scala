package weco.catalogue.internal_model.index

import buildinfo.BuildInfo
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.fields.DenseVectorField
import com.sksamuel.elastic4s.requests.mappings.dynamictemplate.DynamicMapping
import weco.catalogue.internal_model.index.WorksAnalysis.{
  asciifoldingAnalyzer,
  cleanPathAnalyzer,
  exactPathAnalyzer
}
import weco.elasticsearch.IndexConfig

object ImagesIndexConfig extends IndexConfigFields {
  val analysis = WorksAnalysis()
  val emptyDynamicFalseMapping = properties(Seq()).dynamic(DynamicMapping.False)
  val initial = IndexConfig(emptyDynamicFalseMapping, analysis)
  val augmented = IndexConfig(emptyDynamicFalseMapping, analysis)

  val indexed = IndexConfig(
    {
      val inferredData = objectField("inferredData").fields(
        DenseVectorField("features1", dims = 2048),
        DenseVectorField("features2", dims = 2048),
        DenseVectorField("reducedFeatures", dims = 1024),
        keywordField("palette"),
        keywordField("averageColorHex"),
        intField("binSizes").withIndex(false),
        floatField("binMinima").withIndex(false),
        floatField("aspectRatio")
      )

      // This field contains the display document used by the API, but we don't want
      // to index it -- it's just an arbitrary blob of JSON.
      val display = objectField("display").withEnabled(false)

      // This field contains the values we're actually going to query in search.
      val sourceWork = objectField("source")
        .fields(
          // top-level work
          canonicalIdField("id"),
          keywordField("type"),
          keywordField("format.id"),
          keywordField("workType"),
          multilingualFieldWithKeyword("title"),
          multilingualFieldWithKeyword("alternativeTitles"),
          englishTextField("description"),
          englishTextKeywordField("physicalDescription"),
          textField("edition"),
          englishTextField("notes.contents"),
          multilingualField("lettering"),
          // identifiers
          sourceIdentifierField("identifiers.value"),
          // images
          canonicalIdField("images.id"),
          sourceIdentifierField("images.identifiers.value"),
          // items
          canonicalIdField("items.id"),
          sourceIdentifierField("items.identifiers.value"),
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
          // languages
          keywordField("languages.id"),
          labelField("languages.label"),
          // contributors
          canonicalIdField("contributors.agent.id"),
          labelField("contributors.agent.label"),
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
          keywordField("referenceNumber")
        )

      val query = objectField("query")
        .fields(
          canonicalIdField("id"),
          sourceIdentifierField("sourceIdentifier.value"),
          keywordField("locations.license.id"),
          inferredData,
          sourceWork
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
          keywordField("locations.license"),
          keywordField("source.contributors.agent.label"),
          keywordField("source.genres.label"),
          keywordField("source.subjects.label")
        )

      val fields = Seq(
        dateField("modifiedTime"),
        display,
        query,
        aggregatableValues
      )

      // Here we set dynamic strict to be sure the object vaguely looks like an
      // image and contains the core fields, adding DynamicMapping.False in places
      // where we do not need to map every field and can save CPU.
      val mapping = {
        val version = BuildInfo.version.split("\\.").toList
        properties(fields)
          .dynamic(DynamicMapping.Strict)
          .meta(Map(s"model.versions.${version.head}" -> version.tail.head))
      }
      mapping
    },
    analysis
  )
}
