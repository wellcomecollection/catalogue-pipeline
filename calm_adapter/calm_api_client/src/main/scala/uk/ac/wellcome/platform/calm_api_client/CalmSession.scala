package uk.ac.wellcome.platform.calm_api_client

import akka.http.scaladsl.model.headers.Cookie

case class CalmSession(numHits: Int, cookie: Cookie)
