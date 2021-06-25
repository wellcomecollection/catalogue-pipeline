package weco.pipeline.sierra_reader.config.builders

import com.typesafe.config.Config
import weco.pipeline.sierra_reader.config.models.SierraAPIConfig
import weco.typesafe.config.builders.EnrichConfig._

object SierraAPIConfigBuilder {
  def buildSierraConfig(config: Config): SierraAPIConfig =
    SierraAPIConfig(
      apiURL = config.requireString("sierra.apiURL"),
      oauthKey = config.requireString("sierra.oauthKey"),
      oauthSec = config.requireString("sierra.oauthSecret")
    )
}
