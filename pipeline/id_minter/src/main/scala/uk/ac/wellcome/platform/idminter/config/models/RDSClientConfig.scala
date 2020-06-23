package uk.ac.wellcome.platform.idminter.config.models

case class RDSClientConfig(
  primaryHost: String,
  replicaHost: String,
  port: Int,
  username: String,
  password: String
)
