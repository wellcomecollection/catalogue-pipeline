package uk.ac.wellcome.platform.sierra_reader.fixtures

import uk.ac.wellcome.platform.sierra_reader.config.models.SierraAPIConfig

trait WireMockFixture {
  val oauthKey = "key"
  val oauthSecret = "secret"
  val apiUrl = "http://localhost:8080"

  val sierraAPIConfig: SierraAPIConfig = SierraAPIConfig(
    apiURL = apiUrl,
    oauthKey = oauthKey,
    oauthSec = oauthSecret
  )
}
