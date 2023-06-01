package weco.catalogue.internal_model.fixtures.index
import weco.catalogue.internal_model.fixtures.elasticsearch.ElasticsearchFixtures
import scala.io.{Codec, Source}
import com.sksamuel.elastic4s.Index
import weco.fixtures.Fixture

trait IndexFixturesBase extends ElasticsearchFixtures with IndexFixturesE4S {

  protected def getConfig(mappings: String, analysis: String) = {
    val mappings = Source
      .fromResource(mappings)(Codec.UTF8)
      .mkString
      
    val analysis = Source
      .fromResource(analysis)(Codec.UTF8)
      .mkString
  
    s"""{"mappings": $mappings, "settings": {"analysis": $analysis}}"""
  }

  protected def withLocalUnanalysedJsonStore[R]: Fixture[Index, R] = {
    withLocalElasticSearchIndex[R](config =
      s"""{"mappings":${
        Source
          .fromResource("mappings.empty.json")(Codec.UTF8)
          .mkString
      }}""")
  }

}
