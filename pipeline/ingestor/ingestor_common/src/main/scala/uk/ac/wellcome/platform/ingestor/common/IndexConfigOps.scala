package uk.ac.wellcome.platform.ingestor.common

import com.typesafe.config.Config
import uk.ac.wellcome.elasticsearch.{IndexConfig, RefreshInterval}

trait IndexConfigOps {
  implicit class IndexConfigOps[IC <: IndexConfig](indexConfig: IC) {
    def withRefreshIntervalFromConfig(config: Config): IndexConfig = {
      val isReindexing = config.getBoolean(s"es.is_reindexing")
      val interval = if (isReindexing) RefreshInterval.Off else indexConfig.refreshInterval

      object NewConfig extends IndexConfig {
        override def mapping = indexConfig.mapping
        override def analysis = indexConfig.analysis
        override def shards = indexConfig.shards
        override def refreshInterval = interval
      }
      NewConfig
    }
  }
}

