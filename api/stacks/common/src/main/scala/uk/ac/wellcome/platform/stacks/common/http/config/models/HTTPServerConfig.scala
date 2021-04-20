package uk.ac.wellcome.platform.stacks.common.http.config.models

case class HTTPServerConfig(
  host: String,
  port: Int,
  externalBaseURL: String
)
