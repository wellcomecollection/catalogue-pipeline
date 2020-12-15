package uk.ac.wellcome.elasticsearch

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.requests.mappings.{FieldDefinition, ObjectField}
import com.sksamuel.elastic4s.requests.mappings.dynamictemplate.DynamicMapping

trait WorksIndexConfigFields extends IndexConfigFields {

  import WorksAnalysis._

  // Indexing lots of individual fields on Elasticsearch can be very CPU
  // intensive, so here only include fields that are needed for querying in the
  // API.
  def data: ObjectField =
    objectField("data").fields(
      objectField("otherIdentifiers").fields(lowercaseKeyword("value")),
      objectField("format").fields(keywordField("id")),
      asciifoldingTextFieldWithKeyword("title").fields(
        keywordField("keyword"),
        textField("english").analyzer(englishAnalyzer.name),
        textField("shingles").analyzer(shingleAsciifoldingAnalyzer.name)
      ),
      englishTextKeywordField("alternativeTitles"),
      englishTextField("description"),
      englishTextKeywordField("physicalDescription"),
      englishTextKeywordField("lettering"),
      objectField("contributors").fields(
        objectField("agent").fields(label),
      ),
      objectField("subjects").fields(
        label,
        objectField("concepts").fields(label)
      ),
      objectField("genre").fields(
        label,
        objectField("concepts").fields(label)
      ),
      objectField("items").fields(
        objectField("locations").fields(
          keywordField("type"),
          objectField("locationType").fields(keywordField("id")),
          objectField("accessConditions").fields(
            objectField("status").fields(keywordField("type"))
          ),
          objectField("license").fields(keywordField("id"))
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
      objectField("languages").fields(
        label,
        keywordField("id")
      ),
      textField("edition"),
      objectField("notes").fields(englishTextField("content")),
      intField("duration"),
      objectField("collectionPath").fields(
        label,
        textField("path"),
      ),
      objectField("imageData").fields(
        objectField("id").fields(
          canonicalId,
          objectField("sourceIdentifier").fields(lowercaseKeyword("value")),
        )
      ),
      keywordField("workType")
    )
}

sealed trait WorksIndexConfig extends IndexConfig with WorksIndexConfigFields {

  val analysis = WorksAnalysis()

  val dynamicMapping: DynamicMapping = DynamicMapping.False

  def fields: Seq[FieldDefinition with Product with Serializable]

  def mapping = properties(fields).dynamic(dynamicMapping)
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
            .fields(keywordField("keyword")
          ),
          tokenCountField("depth").analyzer("standard")
        )
      )
    )
}

object IdentifiedWorkIndexConfig extends WorksIndexConfig {

  val fields = Seq.empty
}

object DenormalisedWorkIndexConfig extends WorksIndexConfig {

  val fields = Seq.empty

  /** Denormalised relations make index sizes explode from about 5 GB without relations
    * to an esimated 70 GB with relations. According to elastic search documentation
    * (https://www.elastic.co/guide/en/elasticsearch/reference/current/size-your-shards.html), shards
    * sizes should be between 10 GB and 50 GB. Setting number of shards to 2 should result in shards
    * of about 35 GB.
    */
  override val shards = 2
}

object IndexedWorkIndexConfig extends WorksIndexConfig {

  val state = objectField("state").fields(
    canonicalId,
    objectField("sourceIdentifier").fields(lowercaseKeyword("value")),
  )

  val fields =
    Seq(
      state,
      keywordField("type"),
      data,
      objectField("invisibilityReasons").fields(
        keywordField("type"),
      ),
      objectField("deletedReason").fields(
        keywordField("type"),
      )
    )

  /** Denormalised relations make index sizes explode from about 5 GB without relations
    * to an esimated 70 GB with relations. According to elastic search documentation
    * (https://www.elastic.co/guide/en/elasticsearch/reference/current/size-your-shards.html), shards
    * sizes should be between 10 GB and 50 GB. Setting number of shards to 2 should result in shards
    * of about 35 GB.
    */
  override val shards = 2
}
