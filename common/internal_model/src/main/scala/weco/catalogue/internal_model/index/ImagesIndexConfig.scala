package weco.catalogue.internal_model.index

import buildinfo.BuildInfo
import com.sksamuel.elastic4s.ElasticDsl.{keywordField, _}
import com.sksamuel.elastic4s.fields.{DenseVectorField, ObjectField}
import com.sksamuel.elastic4s.requests.mappings.dynamictemplate.DynamicMapping
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
          .withDynamic("false"),

        // This field contains the display document used by the API, but we don't want
        // to index it -- it's just an arbitrary blob of JSON.
        ObjectField("display", enabled = Some(false))
          .withDynamic("true")
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
