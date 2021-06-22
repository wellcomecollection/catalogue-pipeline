package uk.ac.wellcome.platform.ingestor.common

import com.sksamuel.elastic4s.analysis.Analysis
import com.sksamuel.elastic4s.requests.mappings.MappingDefinition
import org.scalatest.funspec.AnyFunSpec
import com.typesafe.config.ConfigFactory
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.elasticsearch.{IndexConfig, RefreshInterval}

import scala.concurrent.duration.DurationInt

object TestConfig extends IndexConfig {
  val analysis: Analysis = Analysis(analyzers = List())
  val mapping: MappingDefinition = MappingDefinition()
  override val refreshInterval = RefreshInterval.On(30.seconds)
}

class IndexConfigOpsTest extends AnyFunSpec with Matchers with IndexConfigOps {
  it("sets the value of RefreshInterval to Off if es.is_reindexing is true") {
    val config = ConfigFactory.load("reindexing.application")
    val newIndexConfig: IndexConfig =
      TestConfig.withRefreshIntervalFromConfig(config)

    newIndexConfig.refreshInterval shouldBe RefreshInterval.Off
  }

  it("maintains the value of RefreshInterval if es.is_reindexing is false") {
    val config = ConfigFactory.load("searching.application")
    val newIndexConfig: IndexConfig =
      TestConfig.withRefreshIntervalFromConfig(config)

    newIndexConfig.refreshInterval shouldBe TestConfig.refreshInterval
  }
}
