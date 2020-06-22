package uk.ac.wellcome.platform.sierra_reader.config.builders

import com.typesafe.config.Config
import uk.ac.wellcome.platform.sierra_reader.config.models.SierraAPIConfig
import uk.ac.wellcome.typesafe.config.builders.EnrichConfig._

object SierraAPIConfigBuilder {
  def buildSierraConfig(config: Config): SierraAPIConfig =
    SierraAPIConfig(
      apiURL = config.requireString("sierra.apiURL"),
      oauthKey = config.requireString("sierra.oauthKey"),
      oauthSec = config.requireString("sierra.oauthSecret")
    )
}
