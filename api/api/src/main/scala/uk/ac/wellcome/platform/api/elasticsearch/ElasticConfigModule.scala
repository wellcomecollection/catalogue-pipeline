package uk.ac.wellcome.platform.api.elasticsearch

import com.google.inject.{Provides, Singleton}
import com.sksamuel.elastic4s.Index
import com.twitter.inject.TwitterModule
import uk.ac.wellcome.elasticsearch.DisplayElasticConfig

object ElasticConfigModule extends TwitterModule {
  private val indexV2name = flag[String]("es.index.v2", "V2 ES index name")

  @Singleton
  @Provides
  def providesElasticConfig(): DisplayElasticConfig =
    DisplayElasticConfig(
      indexV2 = Index(indexV2name())
    )
}
