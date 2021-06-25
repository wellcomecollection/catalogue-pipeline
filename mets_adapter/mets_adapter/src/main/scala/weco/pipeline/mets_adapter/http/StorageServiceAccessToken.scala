package weco.pipeline.mets_adapter.http

import io.circe.generic.extras.JsonKey

case class StorageServiceAccessToken(
  @JsonKey("access_token") accessToken: String,
  @JsonKey("expires_in") expiresIn: Int
)
