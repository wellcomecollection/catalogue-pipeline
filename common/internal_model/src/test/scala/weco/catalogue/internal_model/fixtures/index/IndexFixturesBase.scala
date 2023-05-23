package weco.catalogue.internal_model.fixtures.index
import weco.catalogue.internal_model.fixtures.elasticsearch.ElasticsearchFixtures
import scala.io.{Codec, Source}
import com.sksamuel.elastic4s.Index
import weco.fixtures.Fixture

trait IndexFixturesBase extends ElasticsearchFixtures {

  protected def getConfig(mappings: String) =
    s"""{"mappings":${Source
        .fromResource(mappings)(Codec.UTF8)
        .mkString}}"""

  protected def getConfig(mappings: String, analysis: String) =
    s"""{"mappings":${Source
        .fromResource(mappings)(Codec.UTF8)
        .mkString},
      "settings":{"analysis":   
      ${Source
        .fromResource(analysis)(Codec.UTF8)
        .mkString}
      }}
      """

  protected def withLocalUnanalysedJsonStore[R]: Fixture[Index, R] = {
    withLocalElasticSearchIndex[R](config = getConfig("mappings.empty.v1.json"))
  }

}
