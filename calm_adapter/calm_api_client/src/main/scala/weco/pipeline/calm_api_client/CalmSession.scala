package weco.pipeline.calm_api_client

import org.apache.pekko.http.scaladsl.model.headers.Cookie

case class CalmSession(numHits: Int, cookie: Cookie)
