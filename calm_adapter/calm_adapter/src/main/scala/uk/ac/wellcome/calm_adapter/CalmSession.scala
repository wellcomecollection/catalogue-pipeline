package uk.ac.wellcome.calm_adapter

import akka.http.scaladsl.model.headers.Cookie

case class CalmSession(numHits: Int, cookie: Cookie)
